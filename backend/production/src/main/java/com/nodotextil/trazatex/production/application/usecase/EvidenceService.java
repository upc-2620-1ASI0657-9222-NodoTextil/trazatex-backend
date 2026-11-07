package com.nodotextil.trazatex.production.application.usecase;

import com.nodotextil.trazatex.production.application.dto.Actor;
import com.nodotextil.trazatex.production.application.dto.EvidenceItems;
import com.nodotextil.trazatex.production.application.dto.EvidenceResult;
import com.nodotextil.trazatex.production.application.dto.EvidenceUpload;
import com.nodotextil.trazatex.production.application.exception.StorageNotAvailableException;
import com.nodotextil.trazatex.production.application.exception.StoragePermanentException;
import com.nodotextil.trazatex.production.application.exception.StorageTransientException;
import com.nodotextil.trazatex.production.application.port.in.ManageEvidenceUseCase;
import com.nodotextil.trazatex.production.application.port.out.AuditLogPort;
import com.nodotextil.trazatex.production.application.port.out.BatchRepositoryPort;
import com.nodotextil.trazatex.production.application.port.out.EvidenceRepositoryPort;
import com.nodotextil.trazatex.production.application.port.out.EvidenceRepositoryPort.NewEvidence;
import com.nodotextil.trazatex.production.application.port.out.EvidenceStoragePort;
import com.nodotextil.trazatex.production.application.port.out.TransformationRepositoryPort;
import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.production.domain.exception.FileTooLargeException;
import com.nodotextil.trazatex.production.domain.model.Evidence;
import com.nodotextil.trazatex.production.domain.model.EvidenceOwnerType;
import com.nodotextil.trazatex.production.domain.model.ImageFormat;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * US42. <b>No es {@code @Transactional}</b> a propósito: la subida a Cloudinary puede tardar segundos y no debe ocurrir dentro de
 * una transacción que tenga tomado el lote o la transformación. El dueño se lee sin bloqueo, los archivos se suben y solo al final,
 * en una transacción corta, se escriben las filas; así una división o una transferencia simultánea nunca espera a la subida.
 * (Si la petición trae {@code Idempotency-Key}, el filtro ya abrió una transacción, pero esa solo retiene la fila de la clave:
 * nada de aquí toma bloqueos sobre el lote.)
 *
 * <p>Todo o nada: se validan todos los archivos antes de subir ninguno. Cloudinary que no responde no es un error del usuario: la
 * evidencia queda {@code PENDING} con sus bytes en la cola (misma transacción que su fila) y, tras el primer fallo, el resto de la
 * petición va directo a la cola, de modo que el peor caso es un solo timeout.
 */
@Service
class EvidenceService implements ManageEvidenceUseCase {

    private static final Logger log = LoggerFactory.getLogger(EvidenceService.class);

    private final BatchRepositoryPort batches;
    private final TransformationRepositoryPort transformations;
    private final EvidenceRepositoryPort evidence;
    private final EvidenceStoragePort storage;
    private final AuditLogPort audit;
    private final EvidenceSettings settings;
    private final Clock clock;
    private final TransactionTemplate transaction;

    EvidenceService(BatchRepositoryPort batches, TransformationRepositoryPort transformations, EvidenceRepositoryPort evidence,
                    EvidenceStoragePort storage, AuditLogPort audit, EvidenceSettings settings, Clock clock,
                    PlatformTransactionManager transactionManager) {
        this.batches = batches;
        this.transformations = transformations;
        this.evidence = evidence;
        this.storage = storage;
        this.audit = audit;
        this.settings = settings;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public EvidenceItems attach(EvidenceOwnerType ownerType, UUID ownerId, List<EvidenceUpload> files, Actor actor) {
        requireOwnedBy(ownerType, ownerId, actor);          // lectura sin bloqueo; una empresa ajena es "no existe"
        List<ImageFormat> formats = validate(files);

        List<NewEvidence> created = new ArrayList<>();
        Instant batchTime = clock.instant();
        boolean storageDown = false;
        String reasonDown = null;
        for (int i = 0; i < files.size(); i++) {
            EvidenceUpload file = files.get(i);
            ImageFormat format = formats.get(i);
            UUID id = UUID.randomUUID();
            String filename = sanitize(file.originalFilename());
            // Un microsegundo de diferencia por archivo: así el listado (por fecha) conserva el orden en que llegaron en la petición
            Instant now = batchTime.plus(i, java.time.temporal.ChronoUnit.MICROS);
            if (!storageDown) {
                try {
                    String url = storage.upload(id.toString(), id + "." + format.extension(), format.contentType(), file.content()).url();
                    created.add(new NewEvidence(Evidence.uploaded(id, ownerType, ownerId, actor.companyId(), filename, format,
                            file.content().length, actor.userId(), url, now), null));
                    continue;
                } catch (StoragePermanentException rejected) {
                    // Cloudinary dice que no es una imagen válida: es el escenario 3 de US42, nada queda guardado
                    throw new ValidationException(ErrorCodes.UNSUPPORTED_FILE_FORMAT,
                            "El archivo \"" + filename + "\" no es una imagen válida. Formatos permitidos: " + ImageFormat.ALLOWED_DESCRIPTION + ".",
                            List.of(filename));
                } catch (StorageNotAvailableException unavailable) {
                    log.debug("Almacén de imágenes no disponible ({}): la evidencia {} va a la cola", unavailable.getMessage(), id);
                    storageDown = true;
                    reasonDown = unavailable.getMessage();
                } catch (StorageTransientException failed) {
                    log.warn("La subida de la evidencia {} a Cloudinary falló ({}): queda pendiente en la cola", id, failed.getMessage());
                    storageDown = true;
                    reasonDown = failed.getMessage();
                }
            }
            created.add(new NewEvidence(Evidence.pending(id, ownerType, ownerId, actor.companyId(), filename, format,
                    file.content().length, actor.userId(), now, settings.retry().initialDelay(), reasonDown), file.content()));
        }

        transaction.executeWithoutResult(status -> {
            evidence.saveAll(created);
            long pending = created.stream().filter(n -> n.queuedContent() != null).count();
            audit.record(actor.userId(), actor.companyId(), "EVIDENCE_ATTACHED", ownerType == EvidenceOwnerType.BATCH ? "Batch" : "Transformation",
                    ownerId.toString(), BatchService.changes("files", created.size(), "pendingUpload", pending,
                            "evidenceIds", created.stream().map(n -> n.evidence().id().toString()).toList()));
        });
        return new EvidenceItems(created.stream().map(n -> EvidenceResult.from(n.evidence())).toList());
    }

    @Override
    public EvidenceItems list(EvidenceOwnerType ownerType, UUID ownerId, Actor actor) {
        requireOwnedBy(ownerType, ownerId, actor);
        return new EvidenceItems(evidence.findByOwnerAndCompany(ownerType, ownerId, actor.companyId()).stream()
                .map(EvidenceResult::from).toList());
    }

    private void requireOwnedBy(EvidenceOwnerType ownerType, UUID ownerId, Actor actor) {
        boolean owned = switch (ownerType) {
            case BATCH -> batches.findById(ownerId).filter(b -> b.companyId().equals(actor.companyId())).isPresent();
            case TRANSFORMATION -> transformations.findById(ownerId).filter(t -> t.companyId().equals(actor.companyId())).isPresent();
        };
        if (!owned) {
            throw ownerType == EvidenceOwnerType.BATCH
                    ? new NotFoundException(ErrorCodes.BATCH_NOT_FOUND, "El lote no existe.")
                    : new NotFoundException(ErrorCodes.TRANSFORMATION_NOT_FOUND, "La transformación no existe.");
        }
    }

    /** Todo o nada: si un archivo falla, ninguno se sube ni se guarda. */
    private List<ImageFormat> validate(List<EvidenceUpload> files) {
        if (files == null || files.isEmpty() || files.stream().anyMatch(f -> f == null || f.content() == null || f.content().length == 0)) {
            throw new ValidationException(ErrorCodes.NO_FILES, "Adjunte al menos una fotografía (campo \"files\"); no puede haber archivos vacíos.", null);
        }
        if (files.size() > settings.maxFiles()) {
            throw new ValidationException(ErrorCodes.TOO_MANY_FILES,
                    "Se pueden adjuntar como máximo " + settings.maxFiles() + " fotografías por petición.", null);
        }
        List<String> tooLarge = files.stream().filter(f -> f.content().length > settings.maxFileBytes())
                .map(f -> sanitize(f.originalFilename())).toList();
        if (!tooLarge.isEmpty()) {
            throw new FileTooLargeException("Cada fotografía puede pesar como máximo " + settings.maxFileBytes() / (1024 * 1024) + " MB.", tooLarge);
        }
        List<ImageFormat> formats = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        for (EvidenceUpload file : files) {
            ImageFormat.detect(file.content()).ifPresentOrElse(formats::add, () -> unsupported.add(sanitize(file.originalFilename())));
        }
        if (!unsupported.isEmpty()) {
            throw new ValidationException(ErrorCodes.UNSUPPORTED_FILE_FORMAT,
                    "Formato no soportado. Formatos permitidos: " + ImageFormat.ALLOWED_DESCRIPTION + ".", unsupported);
        }
        return formats;
    }

    /** El nombre solo sirve para mostrar: sin ruta, sin caracteres de control y a lo sumo 255 caracteres. */
    static String sanitize(String filename) {
        if (filename == null || filename.isBlank()) {
            return "foto";
        }
        String name = filename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) {
            return "foto";
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }
}

package com.nodotextil.trazatex.quality.application.usecase;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.EvidenceItems;
import com.nodotextil.trazatex.quality.application.dto.EvidenceResult;
import com.nodotextil.trazatex.quality.application.dto.EvidenceUpload;
import com.nodotextil.trazatex.quality.application.exception.StorageNotAvailableException;
import com.nodotextil.trazatex.quality.application.exception.StoragePermanentException;
import com.nodotextil.trazatex.quality.application.exception.StorageTransientException;
import com.nodotextil.trazatex.quality.application.port.in.ManageEvidenceUseCase;
import com.nodotextil.trazatex.quality.application.port.out.AuditLogPort;
import com.nodotextil.trazatex.quality.application.port.out.EvidenceRepositoryPort;
import com.nodotextil.trazatex.quality.application.port.out.EvidenceRepositoryPort.NewEvidence;
import com.nodotextil.trazatex.quality.application.port.out.EvidenceStoragePort;
import com.nodotextil.trazatex.quality.application.port.out.QualityControlRepositoryPort;
import com.nodotextil.trazatex.quality.application.port.out.QualityFailureRepositoryPort;
import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.quality.domain.exception.FileTooLargeException;
import com.nodotextil.trazatex.quality.domain.model.Evidence;
import com.nodotextil.trazatex.quality.domain.model.EvidenceOwnerType;
import com.nodotextil.trazatex.quality.domain.model.ImageFormat;
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

@Service
class EvidenceService implements ManageEvidenceUseCase {

    private static final Logger log = LoggerFactory.getLogger(EvidenceService.class);

    private final QualityControlRepositoryPort controls;
    private final QualityFailureRepositoryPort failures;
    private final EvidenceRepositoryPort evidence;
    private final EvidenceStoragePort storage;
    private final AuditLogPort audit;
    private final EvidenceSettings settings;
    private final Clock clock;
    private final TransactionTemplate transaction;

    EvidenceService(QualityControlRepositoryPort controls, QualityFailureRepositoryPort failures, EvidenceRepositoryPort evidence,
                    EvidenceStoragePort storage, AuditLogPort audit, EvidenceSettings settings, Clock clock,
                    PlatformTransactionManager transactionManager) {
        this.controls = controls;
        this.failures = failures;
        this.evidence = evidence;
        this.storage = storage;
        this.audit = audit;
        this.settings = settings;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public EvidenceItems attach(EvidenceOwnerType ownerType, UUID ownerId, List<EvidenceUpload> files, Actor actor) {
        requireOwnedBy(ownerType, ownerId, actor);
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
            Instant now = batchTime.plus(i, java.time.temporal.ChronoUnit.MICROS);
            if (!storageDown) {
                try {
                    String url = storage.upload(id.toString(), id + "." + format.extension(), format.contentType(), file.content()).url();
                    created.add(new NewEvidence(Evidence.uploaded(id, ownerType, ownerId, actor.companyId(), filename, format,
                            file.content().length, actor.userId(), url, now), null));
                    continue;
                } catch (StoragePermanentException rejected) {
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
            audit.record(actor.userId(), actor.companyId(), "EVIDENCE_ATTACHED", ownerType == EvidenceOwnerType.CONTROL ? "QualityControl" : "QualityFailure",
                    ownerId.toString(), java.util.Map.of("files", created.size(), "pendingUpload", pending,
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
            case CONTROL -> controls.findById(ownerId).filter(c -> c.companyId().equals(actor.companyId())).isPresent();
            case FAILURE -> failures.findById(ownerId).filter(f -> f.companyId().equals(actor.companyId())).isPresent();
        };
        if (!owned) {
            throw ownerType == EvidenceOwnerType.CONTROL
                    ? new NotFoundException(ErrorCodes.CONTROL_NOT_FOUND, "El control no existe.")
                    : new NotFoundException(ErrorCodes.FAILURE_NOT_FOUND, "La falla no existe.");
        }
    }

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

package com.nodotextil.trazatex.quality.application.usecase;

import com.nodotextil.trazatex.quality.application.exception.StorageNotAvailableException;
import com.nodotextil.trazatex.quality.application.exception.StoragePermanentException;
import com.nodotextil.trazatex.quality.application.exception.StorageTransientException;
import com.nodotextil.trazatex.quality.application.port.in.UploadPendingEvidenceUseCase;
import com.nodotextil.trazatex.quality.application.port.out.EvidenceRepositoryPort;
import com.nodotextil.trazatex.quality.application.port.out.EvidenceRepositoryPort.PendingUpload;
import com.nodotextil.trazatex.quality.application.port.out.EvidenceStoragePort;
import com.nodotextil.trazatex.quality.domain.model.Evidence;
import com.nodotextil.trazatex.quality.domain.model.EvidenceStatus;
import com.nodotextil.trazatex.quality.domain.model.ImageFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * La cola diferida de Cloudinary (ADR-007). Cada evidencia se reclama en una transacción corta (con un <i>lease</i>, así dos jobs no
 * la suben a la vez), se sube <b>fuera</b> de cualquier transacción y su resultado se guarda en otra corta. La subida es idempotente
 * ({@code public_id} = id de la evidencia, sin sobrescribir), así que un reintento tras una respuesta perdida no duplica la imagen.
 *
 * <ul>
 *   <li>Subida correcta → {@code UPLOADED} y se borran sus bytes de la cola.</li>
 *   <li>Cloudinary respondió mal → suma un intento y reprograma con espera creciente; al agotar los intentos, {@code FAILED}.</li>
 *   <li>Cloudinary rechazó el archivo (no es una imagen) → {@code FAILED} de inmediato.</li>
 *   <li>Circuito abierto o sin configurar → se reprograma <b>sin sumar intentos</b>: no se llegó a intentar.</li>
 * </ul>
 * En {@code FAILED} los bytes se conservan (se reencola a mano; ver docs/progress.md).
 */
@Service
class PendingEvidenceUploader implements UploadPendingEvidenceUseCase {

    private static final Logger log = LoggerFactory.getLogger(PendingEvidenceUploader.class);

    private final EvidenceRepositoryPort evidence;
    private final EvidenceStoragePort storage;
    private final EvidenceSettings settings;
    private final Clock clock;
    private final TransactionTemplate transaction;

    PendingEvidenceUploader(EvidenceRepositoryPort evidence, EvidenceStoragePort storage, EvidenceSettings settings, Clock clock,
                            PlatformTransactionManager transactionManager) {
        this.evidence = evidence;
        this.storage = storage;
        this.settings = settings;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public int uploadDue() {
        int processed = 0;
        while (processed < settings.claimsPerRun()) {
            Instant now = clock.instant();
            Optional<PendingUpload> claimed = transaction.execute(status -> evidence.claimNextDue(now, now.plus(settings.lease())));
            if (claimed == null || claimed.isEmpty()) {
                break;
            }
            upload(claimed.get());
            processed++;
        }
        return processed;
    }

    private void upload(PendingUpload pending) {
        Evidence item = pending.evidence();
        String filename = item.id() + "." + ImageFormat.ofContentType(item.contentType()).extension();
        try {
            String url = storage.upload(item.id().toString(), filename, item.contentType(), pending.content()).url();
            save(item, e -> e.markUploaded(url, clock.instant()));
            log.info("Evidencia {} subida a Cloudinary tras {} intento(s) fallido(s)", item.id(), item.attempts());
        } catch (StorageNotAvailableException unavailable) {
            Instant until = clock.instant().plus(settings.notAvailableDelay());
            save(item, e -> e.deferWithoutAttempt(unavailable.getMessage(), until));
            log.debug("Evidencia {}: {}; se vuelve a mirar a las {}", item.id(), unavailable.getMessage(), until);
        } catch (StoragePermanentException rejected) {
            save(item, e -> e.recordPermanentFailure(rejected.getMessage()));
            log.error("Evidencia {} rechazada por Cloudinary ({}): queda FAILED; sus bytes se conservan", item.id(), rejected.getMessage());
        } catch (RuntimeException failed) {
            save(item, e -> e.recordTransientFailure(describe(failed), clock.instant(), settings.retry()));
            if (item.status() == EvidenceStatus.FAILED) {
                log.error("Evidencia {} agotó {} intentos ({}): queda FAILED; sus bytes se conservan", item.id(), item.attempts(), describe(failed));
            } else {
                log.warn("Evidencia {}: intento {} fallido ({}); próximo a las {}", item.id(), item.attempts(), describe(failed), item.nextAttemptAt());
            }
        }
    }

    private void save(Evidence item, java.util.function.Consumer<Evidence> change) {
        change.accept(item);
        transaction.executeWithoutResult(status -> evidence.update(item));
    }

    private static String describe(RuntimeException e) {
        String message = e.getMessage();
        String text = e.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
        return text.length() > 1000 ? text.substring(0, 1000) : text;
    }
}

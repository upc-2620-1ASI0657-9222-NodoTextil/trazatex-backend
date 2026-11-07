package com.nodotextil.trazatex.quality.infrastructure.scheduling;

import com.nodotextil.trazatex.quality.application.port.in.UploadPendingEvidenceUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** La cola diferida de Cloudinary (ADR-007): cada pasada sube lo que está vencido. {@code trazatex.evidence.upload.enabled=false} lo apaga (pruebas). */
@Component
@ConditionalOnProperty(name = "trazatex.evidence.upload.enabled", havingValue = "true", matchIfMissing = true)
class PendingEvidenceUploadJob {

    private static final Logger log = LoggerFactory.getLogger(PendingEvidenceUploadJob.class);

    private final UploadPendingEvidenceUseCase uploads;

    PendingEvidenceUploadJob(UploadPendingEvidenceUseCase uploads) {
        this.uploads = uploads;
    }

    @Scheduled(fixedDelayString = "${trazatex.evidence.upload.job-delay:PT30S}", initialDelayString = "${trazatex.evidence.upload.job-initial-delay:PT10S}")
    void uploadPending() {
        try {
            int processed = uploads.uploadDue();
            if (processed > 0) {
                log.debug("Cola de evidencias: {} procesadas", processed);
            }
        } catch (RuntimeException e) {
            log.error("La pasada de la cola de evidencias falló: {}", e.toString());
        }
    }
}

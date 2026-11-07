package com.nodotextil.trazatex.quality.infrastructure.scheduling;
import com.nodotextil.trazatex.quality.application.port.in.DeleteEvidenceUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;
@Component
class PendingEvidenceDeletionJob {
    private final DeleteEvidenceUseCase deletions;
    PendingEvidenceDeletionJob(DeleteEvidenceUseCase deletions) { this.deletions=deletions; }
    @Scheduled(fixedDelayString="${trazatex.evidence.deletion.job-delay:PT5S}", initialDelayString="PT5S")
    void deletePending() {
        try { deletions.purgeDeleted(); }
        catch (RuntimeException error) { LoggerFactory.getLogger(PendingEvidenceDeletionJob.class).warn("Eliminación de evidencias pendiente de reintento ({})", error.getClass().getSimpleName()); }
    }
}

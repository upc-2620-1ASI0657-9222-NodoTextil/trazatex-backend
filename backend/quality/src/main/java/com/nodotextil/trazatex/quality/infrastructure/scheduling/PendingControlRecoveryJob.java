package com.nodotextil.trazatex.quality.infrastructure.scheduling;

import com.nodotextil.trazatex.quality.application.port.in.RecoverPendingCommandsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "trazatex.quality.start.job-enabled", havingValue = "true", matchIfMissing = true)
class PendingControlRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(PendingControlRecoveryJob.class);

    private final RecoverPendingCommandsUseCase recovery;

    PendingControlRecoveryJob(RecoverPendingCommandsUseCase recovery) {
        this.recovery = recovery;
    }

    @Scheduled(initialDelayString = "${trazatex.quality.start.job-initial-delay:PT10S}", fixedDelayString = "${trazatex.quality.start.job-delay:PT15S}")
    void recoverPendingControls() {
        try {
            int claimed = recovery.recoverPending();
            if (claimed > 0) {
                log.debug("Controles en STARTING reintentados: {}", claimed);
            }
        } catch (RuntimeException e) {
            log.error("Falló una pasada de PendingControlRecoveryJob", e);
        }
    }
}

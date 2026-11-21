package com.nodotextil.trazatex.quality.infrastructure.scheduling;

import com.nodotextil.trazatex.quality.application.port.in.RecoverPendingCommandsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "trazatex.quality.start.job-enabled", havingValue = "true", matchIfMissing = true)
class PendingHoldRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(PendingHoldRecoveryJob.class);

    private final RecoverPendingCommandsUseCase recovery;

    PendingHoldRecoveryJob(RecoverPendingCommandsUseCase recovery) {
        this.recovery = recovery;
    }

    @Scheduled(initialDelayString = "${trazatex.quality.start.hold-job-initial-delay:PT3S}", fixedDelayString = "${trazatex.quality.start.hold-job-delay:PT5S}")
    void recoverPendingHolds() {
        try {
            int claimed = recovery.recoverPendingHolds();
            if (claimed > 0) {
                log.debug("Avisos de falla derivada atendidos: {}", claimed);
            }
        } catch (RuntimeException e) {
            log.error("Falló una pasada de PendingHoldRecoveryJob", e);
        }
    }
}

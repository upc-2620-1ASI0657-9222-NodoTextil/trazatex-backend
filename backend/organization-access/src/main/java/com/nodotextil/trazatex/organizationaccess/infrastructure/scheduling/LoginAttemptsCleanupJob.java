package com.nodotextil.trazatex.organizationaccess.infrastructure.scheduling;

import com.nodotextil.trazatex.organizationaccess.application.port.in.MaintainLoginAttemptsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Limpia {@code login_attempts}: sin esto la tabla crecería con cada correo inexistente que alguien pruebe. */
@Component
class LoginAttemptsCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptsCleanupJob.class);

    private final MaintainLoginAttemptsUseCase maintenance;

    LoginAttemptsCleanupJob(MaintainLoginAttemptsUseCase maintenance) {
        this.maintenance = maintenance;
    }

    @Scheduled(fixedDelayString = "${trazatex.login.cleanup-job-delay}")
    void purgeExpired() {
        int purged = maintenance.purgeExpired();
        if (purged > 0) {
            log.info("Registros de intentos de login vencidos eliminados: {}", purged);
        }
    }
}

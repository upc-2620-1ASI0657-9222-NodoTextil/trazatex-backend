package com.nodotextil.trazatex.organizationaccess.application.usecase;

import com.nodotextil.trazatex.organizationaccess.application.port.in.MaintainLoginAttemptsUseCase;
import com.nodotextil.trazatex.organizationaccess.application.port.out.LoginAttemptsRepositoryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/**
 * Qué se considera vencido: los bloqueos cuyos 15 minutos ya pasaron y los contadores parciales (1 o 2 fallos) sin actividad
 * durante {@code trazatex.login.attempts-retention} (24 h por defecto: pasado ese tiempo los fallos se olvidan).
 */
@Service
@Transactional
class LoginAttemptsMaintenanceService implements MaintainLoginAttemptsUseCase {

    private final LoginAttemptsRepositoryPort attempts;
    private final Clock clock;
    private final Duration idleRetention;

    LoginAttemptsMaintenanceService(LoginAttemptsRepositoryPort attempts, Clock clock,
                                    @Value("${trazatex.login.attempts-retention:PT24H}") Duration idleRetention) {
        this.attempts = attempts;
        this.clock = clock;
        this.idleRetention = idleRetention;
    }

    @Override
    public int purgeExpired() {
        return attempts.deleteExpired(clock.instant(), idleRetention);
    }
}

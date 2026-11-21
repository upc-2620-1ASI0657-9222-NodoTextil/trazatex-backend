package com.nodotextil.trazatex.organizationaccess.application.usecase;

import com.nodotextil.trazatex.organizationaccess.application.port.in.MaintainLoginAttemptsUseCase;
import com.nodotextil.trazatex.organizationaccess.application.port.out.LoginAttemptsRepositoryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

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

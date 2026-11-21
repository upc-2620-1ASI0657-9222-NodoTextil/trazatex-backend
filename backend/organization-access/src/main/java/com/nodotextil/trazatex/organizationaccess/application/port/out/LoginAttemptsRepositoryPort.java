package com.nodotextil.trazatex.organizationaccess.application.port.out;

import com.nodotextil.trazatex.organizationaccess.domain.model.Email;
import com.nodotextil.trazatex.organizationaccess.domain.model.LoginAttempts;

import java.time.Duration;
import java.time.Instant;

public interface LoginAttemptsRepositoryPort {

    LoginAttempts lockOrCreate(Email email, Instant now);

    void save(LoginAttempts attempts);

    void delete(Email email);

    int deleteExpired(Instant now, Duration idleRetention);
}

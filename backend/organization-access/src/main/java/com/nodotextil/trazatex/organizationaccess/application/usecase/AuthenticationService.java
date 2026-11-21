package com.nodotextil.trazatex.organizationaccess.application.usecase;

import com.nodotextil.trazatex.organizationaccess.application.dto.IssuedToken;
import com.nodotextil.trazatex.organizationaccess.application.dto.LoginCommand;
import com.nodotextil.trazatex.organizationaccess.application.dto.LoginResult;
import com.nodotextil.trazatex.organizationaccess.application.dto.UserResult;
import com.nodotextil.trazatex.organizationaccess.application.exception.AccountLockedException;
import com.nodotextil.trazatex.organizationaccess.application.exception.InvalidCredentialsException;
import com.nodotextil.trazatex.organizationaccess.application.port.in.AuthenticateUseCase;
import com.nodotextil.trazatex.organizationaccess.application.port.out.LoginAttemptsRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.PasswordHasherPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.TokenIssuerPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.UserRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.domain.model.Email;
import com.nodotextil.trazatex.organizationaccess.domain.model.LoginAttempts;
import com.nodotextil.trazatex.organizationaccess.domain.model.RequiredFields;
import com.nodotextil.trazatex.organizationaccess.domain.model.User;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
@Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
class AuthenticationService implements AuthenticateUseCase {

    private final UserRepositoryPort users;
    private final LoginAttemptsRepositoryPort attempts;
    private final PasswordHasherPort hasher;
    private final TokenIssuerPort tokens;
    private final Clock clock;
    private final String paddingHash;

    AuthenticationService(UserRepositoryPort users, LoginAttemptsRepositoryPort attempts, PasswordHasherPort hasher,
                          TokenIssuerPort tokens, Clock clock) {
        this.users = users;
        this.attempts = attempts;
        this.hasher = hasher;
        this.tokens = tokens;
        this.clock = clock;
        this.paddingHash = hasher.hash("relleno-para-igualar-tiempos");
    }

    @Override
    public LoginResult login(LoginCommand command) {
        new RequiredFields().text("email", command.email()).text("password", command.password()).verify();
        Instant now = clock.instant();

        Optional<Email> email = normalize(command.email());
        if (email.isEmpty()) {
            hasher.matches(command.password(), paddingHash);
            throw new InvalidCredentialsException();
        }
        LoginAttempts history = attempts.lockOrCreate(email.get(), now);
        if (history.isLocked(now)) {
            throw new AccountLockedException(history.lockedUntil());
        }

        Optional<User> user = users.findByEmail(email.get()).filter(User::isActive);
        boolean correct = hasher.matches(command.password(), user.map(User::passwordHash).orElse(paddingHash));
        if (user.isEmpty() || !correct) {
            history.recordFailure(now);
            attempts.save(history);
            if (history.isLocked(now)) {
                throw new AccountLockedException(history.lockedUntil());
            }
            throw new InvalidCredentialsException();
        }

        attempts.delete(email.get());
        IssuedToken token = tokens.issue(user.get(), now);
        return new LoginResult(token.token(), "Bearer", token.expiresAt(), UserResult.from(user.get()));
    }

    private static Optional<Email> normalize(String rawEmail) {
        try {
            return Optional.of(new Email(rawEmail));
        } catch (ValidationException invalidFormat) {
            return Optional.empty();
        }
    }
}

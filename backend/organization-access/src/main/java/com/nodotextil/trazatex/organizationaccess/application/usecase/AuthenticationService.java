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

/**
 * US05, RNF-06, ADR-005. Lo que ve quien llama es idéntico exista o no la cuenta:
 * <ul>
 *   <li><b>Intentos por correo normalizado</b> ({@link LoginAttempts}), no por usuario: un correo sin cuenta se bloquea igual
 *       que uno con cuenta, así que el 423 no revela si la cuenta existe. La fila del correo se bloquea ({@code FOR UPDATE})
 *       mientras se cuenta: intentos simultáneos no se saltan el límite de 3.</li>
 *   <li><b>Tiempo</b>: fuera del bloqueo siempre se hace una verificación BCrypt, contra el hash real si el usuario existe y
 *       está activo y contra un hash de relleno (mismo costo) si no existe o está inactivo. Bloqueado no se verifica nada,
 *       exista o no la cuenta.</li>
 *   <li>Los fallos <b>no revierten</b> la transacción ({@code noRollbackFor}): el contador tiene que quedar guardado aunque la
 *       respuesta sea un error.</li>
 * </ul>
 * Un correo con formato inválido no se registra (no puede ser una cuenta): siempre 401 genérico. El estado de la empresa no
 * impide iniciar sesión (decisión del usuario): una empresa INACTIVE entra en solo lectura.
 */
@Service
@Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
class AuthenticationService implements AuthenticateUseCase {

    private final UserRepositoryPort users;
    private final LoginAttemptsRepositoryPort attempts;
    private final PasswordHasherPort hasher;
    private final TokenIssuerPort tokens;
    private final Clock clock;
    /** Hash de relleno con el mismo costo que los reales: se verifica contra él cuando no hay un usuario activo con ese correo. */
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

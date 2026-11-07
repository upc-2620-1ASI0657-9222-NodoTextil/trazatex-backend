package com.nodotextil.trazatex.organizationaccess.application.usecase;

import com.nodotextil.trazatex.organizationaccess.application.dto.IssuedToken;
import com.nodotextil.trazatex.organizationaccess.application.dto.LoginCommand;
import com.nodotextil.trazatex.organizationaccess.application.exception.AccountLockedException;
import com.nodotextil.trazatex.organizationaccess.application.exception.InvalidCredentialsException;
import com.nodotextil.trazatex.organizationaccess.application.port.out.LoginAttemptsRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.PasswordHasherPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.TokenIssuerPort;
import com.nodotextil.trazatex.organizationaccess.application.port.out.UserRepositoryPort;
import com.nodotextil.trazatex.organizationaccess.domain.model.Email;
import com.nodotextil.trazatex.organizationaccess.domain.model.LoginAttempts;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import com.nodotextil.trazatex.organizationaccess.domain.model.User;
import com.nodotextil.trazatex.organizationaccess.domain.model.UserStatus;
import com.nodotextil.trazatex.sharedkernel.testsupport.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Con el tiempo de respuesta no se puede saber si una cuenta existe: fuera del bloqueo hay <b>una</b> verificación BCrypt
 * en todos los casos (contra el hash real o contra uno de relleno del mismo costo) y bloqueado no hay ninguna. Se comprueba
 * contando las verificaciones y el costo del hash, no midiendo milisegundos (eso sería inestable).
 */
class AuthenticationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private static final Email EMAIL = new Email("ana@hilos.pe");

    /** BCrypt real, que registra cada verificación y contra qué hash se hizo. */
    static class RecordingHasher implements PasswordHasherPort {
        private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        final List<String> verifiedAgainst = new ArrayList<>();

        @Override
        public String hash(String rawPassword) {
            return encoder.encode(rawPassword);
        }

        @Override
        public boolean matches(String rawPassword, String hash) {
            verifiedAgainst.add(hash);
            return encoder.matches(rawPassword, hash);
        }
    }

    private final RecordingHasher hasher = new RecordingHasher();
    private final UserRepositoryPort users = mock(UserRepositoryPort.class);
    private final LoginAttemptsRepositoryPort attempts = mock(LoginAttemptsRepositoryPort.class);
    private final TokenIssuerPort tokens = mock(TokenIssuerPort.class);
    private final MutableClock clock = new MutableClock(NOW);
    private AuthenticationService service;
    private String realHash;

    @BeforeEach
    void setUp() {
        service = new AuthenticationService(users, attempts, hasher, tokens, clock);
        realHash = hasher.hash("contraseña-correcta");
        hasher.verifiedAgainst.clear();
        when(attempts.lockOrCreate(any(), any())).thenAnswer(invocation -> LoginAttempts.none(invocation.getArgument(0), NOW));
        when(tokens.issue(any(), any())).thenReturn(new IssuedToken("jwt", NOW.plus(Duration.ofHours(8))));
    }

    private User user(UserStatus status) {
        return User.restore(UUID.randomUUID(), UUID.randomUUID(), EMAIL, "Ana", "Pérez", null, realHash, Role.COMPANY_ADMIN, status, NOW);
    }

    private static LoginCommand login(String email, String password) {
        return new LoginCommand(email, password);
    }

    private static int bcryptCost(String hash) {
        return Integer.parseInt(hash.split("\\$")[2]);
    }

    @Test
    void anExistingActiveUserWithAWrongPasswordCostsOneVerification() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.login(login("ana@hilos.pe", "incorrecta"))).isInstanceOf(InvalidCredentialsException.class);

        assertThat(hasher.verifiedAgainst).containsExactly(realHash);
    }

    @Test
    void anUnknownEmailAlsoCostsOneVerificationAgainstADummyHashOfTheSameCost() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(login("ana@hilos.pe", "incorrecta"))).isInstanceOf(InvalidCredentialsException.class);

        assertThat(hasher.verifiedAgainst).hasSize(1).doesNotContain(realHash);
        assertThat(bcryptCost(hasher.verifiedAgainst.get(0))).isEqualTo(bcryptCost(realHash));
    }

    @Test
    void anInactiveUserAlsoCostsOneVerificationAgainstTheDummyHashEvenWithTheRightPassword() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(UserStatus.INACTIVE)));

        assertThatThrownBy(() -> service.login(login("ana@hilos.pe", "contraseña-correcta"))).isInstanceOf(InvalidCredentialsException.class);

        assertThat(hasher.verifiedAgainst).hasSize(1).doesNotContain(realHash);
    }

    @Test
    void aMalformedEmailAlsoCostsOneVerification() {
        assertThatThrownBy(() -> service.login(login("no-es-un-correo", "x"))).isInstanceOf(InvalidCredentialsException.class);

        assertThat(hasher.verifiedAgainst).hasSize(1);
        verify(attempts, never()).lockOrCreate(any(), any());
    }

    @Test
    void whileLockedNothingIsVerifiedWhetherOrNotTheAccountExists() {
        LoginAttempts locked = LoginAttempts.restore(EMAIL, 0, NOW.plus(Duration.ofMinutes(10)), NOW);
        doReturn(locked).when(attempts).lockOrCreate(any(), any());
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.login(login("ana@hilos.pe", "contraseña-correcta"))).isInstanceOf(AccountLockedException.class);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login(login("ana@hilos.pe", "contraseña-correcta"))).isInstanceOf(AccountLockedException.class);

        assertThat(hasher.verifiedAgainst).isEmpty();
        verify(users, never()).findByEmail(any());
    }

    @Test
    void theSameAttemptsAreCountedForAnExistingAndAnUnknownEmail() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
        LoginAttempts history = LoginAttempts.none(EMAIL, NOW);
        doReturn(history).when(attempts).lockOrCreate(any(), any());

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> service.login(login("Ana@Hilos.PE", "x"))).isInstanceOf(InvalidCredentialsException.class);
        }
        assertThatThrownBy(() -> service.login(login("ana@hilos.pe", "x"))).isInstanceOf(AccountLockedException.class);

        assertThat(history.isLocked(NOW)).isTrue();
        verify(attempts, times(3)).lockOrCreate(EMAIL, NOW);
    }

    @Test
    void aSuccessfulLoginForgetsThePreviousFailuresAndIssuesTheToken() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(UserStatus.ACTIVE)));

        var result = service.login(login("ana@hilos.pe", "contraseña-correcta"));

        assertThat(result.token()).isEqualTo("jwt");
        assertThat(hasher.verifiedAgainst).containsExactly(realHash);
        verify(attempts).delete(EMAIL);
    }
}

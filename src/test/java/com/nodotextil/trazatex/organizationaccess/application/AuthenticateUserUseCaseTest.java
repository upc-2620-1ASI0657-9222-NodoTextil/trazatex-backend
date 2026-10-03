package com.nodotextil.trazatex.organizationaccess.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.nodotextil.trazatex.organizationaccess.application.AuthenticateUserUseCase.AuthenticationResult;
import com.nodotextil.trazatex.organizationaccess.application.port.AccessTokenIssuer;
import com.nodotextil.trazatex.organizationaccess.application.port.AccessTokenIssuer.IssuedAccessToken;
import com.nodotextil.trazatex.organizationaccess.domain.InvalidCredentialsException;
import com.nodotextil.trazatex.organizationaccess.domain.OrganizationUser;
import com.nodotextil.trazatex.organizationaccess.domain.Role;
import com.nodotextil.trazatex.organizationaccess.domain.UserStatus;
import com.nodotextil.trazatex.organizationaccess.support.FixedClocks;
import com.nodotextil.trazatex.organizationaccess.support.InMemoryOrganizationUserRepository;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthenticateUserUseCaseTest {

	private static final LocalDateTime START = LocalDateTime.of(2026, 10, 1, 9, 0);
	private static final String PASSWORD = "a-strong-password-123";

	private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
	private final InMemoryOrganizationUserRepository users = new InMemoryOrganizationUserRepository();
	private final FixedClocks.Mutable clock = new FixedClocks.Mutable(START);
	private final AccessTokenIssuer issuer = user -> new IssuedAccessToken(
			"token-for-" + user.id(), 3600);
	private AuthenticateUserUseCase useCase;

	@BeforeEach
	void setUp() {
		useCase = new AuthenticateUserUseCase(users, encoder, issuer, clock);
	}

	private OrganizationUser saveUser(Role role, UUID companyId, UserStatus status) {
		return users.save(new OrganizationUser(UUID.randomUUID(), "ana@example.com", "Ana",
				"Lopez", null, encoder.encode(PASSWORD), role, companyId, status, 0, null,
				START));
	}

	@Test
	void logsInWithEmailAndPasswordIgnoringEmailCase() {
		UUID companyId = UUID.randomUUID();
		OrganizationUser user = saveUser(Role.COMPANY_ADMIN, companyId, UserStatus.ACTIVE);

		AuthenticationResult result = useCase.execute(" ANA@Example.com ", PASSWORD);

		assertThat(result.accessToken()).isEqualTo("token-for-" + user.id());
		assertThat(result.expiresInSeconds()).isEqualTo(3600);
		assertThat(result.userId()).isEqualTo(user.id());
		assertThat(result.companyId()).isEqualTo(companyId);
		assertThat(result.role()).isEqualTo("COMPANY_ADMIN");
	}

	@Test
	void licenseOwnerLogsInWithoutCompany() {
		saveUser(Role.LICENSE_OWNER, null, UserStatus.ACTIVE);

		assertThat(useCase.execute("ana@example.com", PASSWORD).companyId()).isNull();
	}

	@Test
	void wrongPasswordAndUnknownEmailFailTheSameWay() {
		saveUser(Role.OPERATOR, UUID.randomUUID(), UserStatus.ACTIVE);

		Throwable wrongPassword = catchThrowable(() -> useCase.execute("ana@example.com", "wrong"));
		Throwable unknownEmail = catchThrowable(() -> useCase.execute("nobody@example.com", PASSWORD));

		assertThat(wrongPassword).isInstanceOf(InvalidCredentialsException.class);
		assertThat(unknownEmail).isInstanceOf(InvalidCredentialsException.class);
		assertThat(wrongPassword.getMessage()).isEqualTo(unknownEmail.getMessage());
	}

	@Test
	void nullCredentialsAreRejected() {
		assertThatThrownBy(() -> useCase.execute(null, null))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void inactiveUserCannotLogIn() {
		saveUser(Role.OPERATOR, UUID.randomUUID(), UserStatus.INACTIVE);
		assertThatThrownBy(() -> useCase.execute("ana@example.com", PASSWORD))
				.isInstanceOf(InvalidCredentialsException.class)
				.hasMessage("Invalid credentials");
	}

	@Test
	void pendingUserCannotLogIn() {
		saveUser(Role.OPERATOR, UUID.randomUUID(), UserStatus.PENDING);
		assertThatThrownBy(() -> useCase.execute("ana@example.com", PASSWORD))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void thirdFailedAttemptLocksTheAccountForFifteenMinutes() {
		OrganizationUser user = saveUser(Role.OPERATOR, UUID.randomUUID(), UserStatus.ACTIVE);

		fail();
		fail();
		assertThat(users.findById(user.id()).orElseThrow().lockedUntil()).isNull();
		fail();

		OrganizationUser locked = users.findById(user.id()).orElseThrow();
		assertThat(locked.failedLoginAttempts()).isEqualTo(3);
		assertThat(locked.lockedUntil()).isEqualTo(START.plusMinutes(15));

		// even the right password is refused while locked, and the lock is not extended
		clock.advanceMinutes(14);
		assertThatThrownBy(() -> useCase.execute("ana@example.com", PASSWORD))
				.isInstanceOf(InvalidCredentialsException.class);
		assertThat(users.findById(user.id()).orElseThrow().lockedUntil())
				.isEqualTo(START.plusMinutes(15));
	}

	@Test
	void canLogInAgainOnceTheLockExpires() {
		OrganizationUser user = saveUser(Role.OPERATOR, UUID.randomUUID(), UserStatus.ACTIVE);
		fail();
		fail();
		fail();

		clock.advanceMinutes(15);
		AuthenticationResult result = useCase.execute("ana@example.com", PASSWORD);

		assertThat(result.userId()).isEqualTo(user.id());
		OrganizationUser after = users.findById(user.id()).orElseThrow();
		assertThat(after.failedLoginAttempts()).isZero();
		assertThat(after.lockedUntil()).isNull();
	}

	@Test
	void failureAfterAnExpiredLockStartsTheCountOver() {
		OrganizationUser user = saveUser(Role.OPERATOR, UUID.randomUUID(), UserStatus.ACTIVE);
		fail();
		fail();
		fail();

		clock.advanceMinutes(16);
		fail();

		OrganizationUser after = users.findById(user.id()).orElseThrow();
		assertThat(after.failedLoginAttempts()).isEqualTo(1);
		assertThat(after.lockedUntil()).isNull();
	}

	@Test
	void successfulLoginResetsTheFailedAttempts() {
		OrganizationUser user = saveUser(Role.OPERATOR, UUID.randomUUID(), UserStatus.ACTIVE);
		fail();
		fail();

		useCase.execute("ana@example.com", PASSWORD);

		assertThat(users.findById(user.id()).orElseThrow().failedLoginAttempts()).isZero();
		fail();
		fail();
		assertThat(users.findById(user.id()).orElseThrow().lockedUntil()).isNull();
	}

	private void fail() {
		assertThatThrownBy(() -> useCase.execute("ana@example.com", "wrong-password"))
				.isInstanceOf(InvalidCredentialsException.class);
	}
}

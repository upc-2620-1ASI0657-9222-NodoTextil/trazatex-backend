package com.nodotextil.trazatex.organizationaccess.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AdminRequestAndUserStatusTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 9, 0);

	private AdminRequest request() {
		return AdminRequest.create(UUID.randomUUID(), "Rosa@Example.com", "Rosa", "Diaz",
				"Manager", UUID.randomUUID(), NOW);
	}

	@Test
	void newRequestIsPendingAndKeepsNamesAndJobTitle() {
		AdminRequest request = request();

		assertThat(request.isPending()).isTrue();
		assertThat(request.email()).isEqualTo("rosa@example.com");
		assertThat(request.firstName()).isEqualTo("Rosa");
		assertThat(request.lastName()).isEqualTo("Diaz");
		assertThat(request.jobTitle()).isEqualTo("Manager");
		assertThat(request.decidedAt()).isNull();
	}

	@Test
	void aPendingRequestCanBeApprovedOrRejectedOnce() {
		UUID owner = UUID.randomUUID();

		AdminRequest approved = request().approve(owner, NOW.plusDays(1));
		AdminRequest rejected = request().reject(owner, NOW.plusDays(1));

		assertThat(approved.status()).isEqualTo(AdminRequestStatus.APPROVED);
		assertThat(approved.decidedByUserId()).isEqualTo(owner);
		assertThat(approved.decidedAt()).isEqualTo(NOW.plusDays(1));
		assertThat(rejected.status()).isEqualTo(AdminRequestStatus.REJECTED);
		assertThatThrownBy(() -> approved.reject(owner, NOW))
				.isInstanceOf(OrganizationConflictException.class);
		assertThatThrownBy(() -> rejected.approve(owner, NOW))
				.isInstanceOf(OrganizationConflictException.class);
	}

	@Test
	void aUserCanBeInactivatedAndReactivatedButNeverReturnsToPending() {
		OrganizationUser user = OrganizationUser.create("a@example.com", "Ana", "Lopez", null,
				"hash", Role.OPERATOR, UUID.randomUUID(), NOW);

		OrganizationUser inactive = user.withStatus(UserStatus.INACTIVE);

		assertThat(inactive.status()).isEqualTo(UserStatus.INACTIVE);
		assertThat(inactive.isActive()).isFalse();
		assertThat(inactive.withStatus(UserStatus.ACTIVE).isActive()).isTrue();
		assertThatThrownBy(() -> user.withStatus(UserStatus.PENDING))
				.isInstanceOf(OrganizationValidationException.class);
		assertThatThrownBy(() -> user.withStatus(null))
				.isInstanceOf(OrganizationValidationException.class);
	}
}

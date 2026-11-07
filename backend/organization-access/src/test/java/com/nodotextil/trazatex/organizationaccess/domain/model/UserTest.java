package com.nodotextil.trazatex.organizationaccess.domain.model;

import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private static final UUID COMPANY = UUID.randomUUID();

    private User user(Role role) {
        UUID company = role.belongsToCompany() ? COMPANY : null;
        return User.createActive(UUID.randomUUID(), company, new Email("u@textil.pe"), "Ana", "Pérez", "Jefa", "hash", role, NOW);
    }

    @Test
    void newUserIsActiveWithoutFailures() {
        User user = user(Role.OPERATOR);

        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.fullName()).isEqualTo("Ana Pérez");
    }

    @Test
    void ownerHasNoCompanyAndTheOthersMust() {
        assertThat(user(Role.LICENSE_OWNER).companyId()).isNull();
        assertThatThrownBy(() -> User.createActive(UUID.randomUUID(), null, new Email("u@textil.pe"), "A", "B", null, "h", Role.OPERATOR, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> User.createActive(UUID.randomUUID(), COMPANY, new Email("u@textil.pe"), "A", "B", null, "h", Role.LICENSE_OWNER, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void inactiveUserIsNotActiveAndCanBeReactivated() {
        User user = user(Role.OPERATOR);
        user.deactivate(1);

        assertThat(user.isActive()).isFalse();
        user.activate();
        assertThat(user.isActive()).isTrue();
    }

    @Test
    void cannotDeactivateTheLastActiveAdmin() {
        User admin = user(Role.COMPANY_ADMIN);

        assertThatThrownBy(() -> admin.deactivate(1))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("LAST_ADMIN_REQUIRED");
        assertThat(admin.isActive()).isTrue();

        admin.deactivate(2);
        assertThat(admin.status()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    void operatorCanBeDeactivatedEvenIfThereIsOneAdmin() {
        User operator = user(Role.OPERATOR);
        operator.deactivate(1);
        assertThat(operator.status()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    void repeatingTheSameTransitionConflicts() {
        User user = user(Role.OPERATOR);

        assertThatThrownBy(user::activate).isInstanceOf(ConflictException.class);
        user.deactivate(1);
        assertThatThrownBy(() -> user.deactivate(1)).isInstanceOf(ConflictException.class);
    }
}

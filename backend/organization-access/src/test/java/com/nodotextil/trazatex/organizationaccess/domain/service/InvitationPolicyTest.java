package com.nodotextil.trazatex.organizationaccess.domain.service;

import com.nodotextil.trazatex.organizationaccess.domain.model.License;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvitationPolicyTest {

    private final License license = License.create(UUID.randomUUID(), 5, 2, Instant.parse("2026-10-19T12:00:00Z"));

    @Test
    void allowsInvitingWhileThereIsCapacity() {
        assertThatCode(() -> InvitationPolicy.ensureCanInvite(license, Role.OPERATOR, 1, false, false)).doesNotThrowAnyException();
    }

    @Test
    void rejectsWhenUsedSlotsReachTheLimit() {
        assertThatThrownBy(() -> InvitationPolicy.ensureCanInvite(license, Role.OPERATOR, 2, false, false))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("USER_LIMIT_REACHED");
        assertThatThrownBy(() -> InvitationPolicy.ensureCanInvite(license, Role.COMPANY_ADMIN, 2, false, false))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("USER_LIMIT_REACHED");
    }

    @Test
    void rejectsAnEmailThatAlreadyHasAnAccount() {
        assertThatThrownBy(() -> InvitationPolicy.ensureCanInvite(license, Role.OPERATOR, 0, true, false))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("EMAIL_ALREADY_REGISTERED");
    }

    @Test
    void rejectsAnEmailWithAnotherPendingInvitation() {
        assertThatThrownBy(() -> InvitationPolicy.ensureCanInvite(license, Role.OPERATOR, 0, false, true))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("INVITATION_ALREADY_PENDING");
    }

    @Test
    void theOwnerInvitationIgnoresCompanyCapacity() {
        assertThatCode(() -> InvitationPolicy.ensureCanInvite(license, Role.LICENSE_OWNER, 999, false, false)).doesNotThrowAnyException();
    }
}

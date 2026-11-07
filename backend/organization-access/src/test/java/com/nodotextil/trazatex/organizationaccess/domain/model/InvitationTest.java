package com.nodotextil.trazatex.organizationaccess.domain.model;

import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import com.nodotextil.trazatex.sharedkernel.exception.ForbiddenException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvitationTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private static final Instant JUST_BEFORE_EXPIRY = NOW.plus(Duration.ofDays(7)).minusSeconds(1);
    private static final Instant EXPIRY = NOW.plus(Duration.ofDays(7));
    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();

    private Invitation operatorInvitation() {
        return Invitation.issue(UUID.randomUUID(), InvitationToken.generate(), new Email("op@textil.pe"),
                "Luis", "Rojas", "Tejedor", Role.OPERATOR, COMPANY, ADMIN, NOW);
    }

    private Invitation adminInvitationWithoutNames() {
        return Invitation.issue(UUID.randomUUID(), InvitationToken.generate(), new Email("admin@textil.pe"),
                null, null, null, Role.COMPANY_ADMIN, COMPANY, UUID.randomUUID(), NOW);
    }

    private Invitation ownerInvitation() {
        return Invitation.issue(UUID.randomUUID(), InvitationToken.generate(), new Email("owner@nodotextil.pe"),
                "Dueña", "Licencia", null, Role.LICENSE_OWNER, null, null, NOW);
    }

    @Test
    void issuedPendingAndExpiresInSevenDays() {
        Invitation invitation = operatorInvitation();

        assertThat(invitation.status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(invitation.createdAt()).isEqualTo(NOW);
        assertThat(invitation.expiresAt()).isEqualTo(EXPIRY);
        assertThat(invitation.fullName()).isEqualTo("Luis Rojas");
        assertThat(invitation.occupiesSlot(NOW)).isTrue();
    }

    @Test
    void operatorInvitationRequiresNamesAndPosition() {
        assertThatThrownBy(() -> Invitation.issue(UUID.randomUUID(), InvitationToken.generate(), new Email("op@textil.pe"),
                "Luis", " ", null, Role.OPERATOR, COMPANY, ADMIN, NOW))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.details()).containsExactly("lastName", "position"));
    }

    @Test
    void displayNameIsKeptAsGivenWithoutSplittingItOrDerivedFromNamesAndPosition() {
        Invitation fromRequest = Invitation.issue(UUID.randomUUID(), InvitationToken.generate(), new Email("marta@textil.pe"),
                "Marta Quispe de la Cruz", null, null, "Gerente", Role.COMPANY_ADMIN, COMPANY, ADMIN, NOW);

        assertThat(fromRequest.displayName()).isEqualTo("Marta Quispe de la Cruz");
        assertThat(fromRequest.fullName()).isEqualTo("Marta Quispe de la Cruz");
        assertThat(fromRequest.firstName()).isNull();
        assertThat(fromRequest.lastName()).isNull();
        assertThat(operatorInvitation().displayName()).isEqualTo("Luis Rojas");
        assertThat(ownerInvitation().displayName()).isEqualTo("Dueña Licencia");
    }

    @Test
    void adminAndOwnerInvitationsMayOmitPersonalData() {
        assertThat(adminInvitationWithoutNames().fullName()).isNull();
        assertThat(ownerInvitation().companyId()).isNull();
    }

    @Test
    void onlyTheOwnerInvitationHasNoCompany() {
        assertThatThrownBy(() -> Invitation.issue(UUID.randomUUID(), InvitationToken.generate(), new Email("a@b.pe"),
                null, null, null, Role.COMPANY_ADMIN, null, ADMIN, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Invitation.issue(UUID.randomUUID(), InvitationToken.generate(), new Email("a@b.pe"),
                null, null, null, Role.LICENSE_OWNER, COMPANY, null, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ownerInvitationDoesNotOccupyACompanySlot() {
        assertThat(ownerInvitation().occupiesSlot(NOW)).isFalse();
    }

    @Test
    void acceptCreatesAnActiveUserWithTheInvitationRoleAndCompanyAndMarksAccepted() {
        Invitation invitation = operatorInvitation();

        User user = invitation.accept(UUID.randomUUID(), "Luis", "Rojas Díaz", "hash", NOW.plusSeconds(3600));

        assertThat(invitation.status()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.role()).isEqualTo(Role.OPERATOR);
        assertThat(user.companyId()).isEqualTo(COMPANY);
        assertThat(user.email()).isEqualTo(new Email("op@textil.pe"));
        assertThat(user.position()).isEqualTo("Tejedor");
        assertThat(user.lastName()).isEqualTo("Rojas Díaz");
        assertThat(invitation.occupiesSlot(NOW)).isFalse();
    }

    @Test
    void ownerAcceptanceCreatesAUserWithoutCompany() {
        User owner = ownerInvitation().accept(UUID.randomUUID(), "Dueña", "Licencia", "hash", NOW);
        assertThat(owner.role()).isEqualTo(Role.LICENSE_OWNER);
        assertThat(owner.companyId()).isNull();
    }

    @Test
    void acceptWorksUntilTheLastSecond() {
        assertThat(operatorInvitation().accept(UUID.randomUUID(), "Luis", "Rojas", "hash", JUST_BEFORE_EXPIRY)).isNotNull();
    }

    @Test
    void expiredInvitationIsNeverAcceptedEvenIfStillStoredAsPending() {
        Invitation invitation = operatorInvitation();

        assertThat(invitation.status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(invitation.effectiveStatus(EXPIRY)).isEqualTo(InvitationStatus.EXPIRED);
        assertThatThrownBy(() -> invitation.accept(UUID.randomUUID(), "Luis", "Rojas", "hash", EXPIRY))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("INVITATION_EXPIRED");
        assertThat(invitation.status()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void expiredInvitationDoesNotOccupyASlotEvenBeforeTheJobRuns() {
        Invitation invitation = operatorInvitation();
        assertThat(invitation.occupiesSlot(JUST_BEFORE_EXPIRY)).isTrue();
        assertThat(invitation.occupiesSlot(EXPIRY)).isFalse();
    }

    @Test
    void cancelledInvitationCannotBeAccepted() {
        Invitation invitation = operatorInvitation();
        invitation.cancel(ADMIN, NOW);

        assertThatThrownBy(() -> invitation.accept(UUID.randomUUID(), "Luis", "Rojas", "hash", NOW))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("INVITATION_CANCELLED");
    }

    @Test
    void acceptedInvitationCannotBeUsedAgain() {
        Invitation invitation = operatorInvitation();
        invitation.accept(UUID.randomUUID(), "Luis", "Rojas", "hash", NOW);

        assertThatThrownBy(() -> invitation.accept(UUID.randomUUID(), "Otro", "Usuario", "hash", NOW))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("INVITATION_ALREADY_ACCEPTED");
    }

    @Test
    void acceptWithoutNamesFailsAndLeavesTheInvitationPending() {
        Invitation invitation = adminInvitationWithoutNames();

        assertThatThrownBy(() -> invitation.accept(UUID.randomUUID(), " ", null, "hash", NOW))
                .isInstanceOf(ValidationException.class);
        assertThat(invitation.status()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void onlyTheIssuerCancelsAPendingInvitation() {
        Invitation invitation = operatorInvitation();

        invitation.cancel(ADMIN, NOW);

        assertThat(invitation.status()).isEqualTo(InvitationStatus.CANCELLED);
        assertThat(invitation.occupiesSlot(NOW)).isFalse();
    }

    @Test
    void anotherUserCannotCancel() {
        Invitation invitation = operatorInvitation();

        assertThatThrownBy(() -> invitation.cancel(UUID.randomUUID(), NOW))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code").isEqualTo("INVITATION_NOT_ISSUER");
        assertThat(invitation.status()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void bootstrapOwnerInvitationCannotBeCancelledByAnyone() {
        Invitation invitation = ownerInvitation();
        assertThatThrownBy(() -> invitation.cancel(UUID.randomUUID(), NOW)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> invitation.cancel(null, NOW)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void acceptedCancelledOrExpiredInvitationsAreNotCancellableAndKeepTheirState() {
        Invitation accepted = operatorInvitation();
        accepted.accept(UUID.randomUUID(), "Luis", "Rojas", "hash", NOW);
        Invitation cancelled = operatorInvitation();
        cancelled.cancel(ADMIN, NOW);
        Invitation expired = operatorInvitation();

        assertThatThrownBy(() -> accepted.cancel(ADMIN, NOW)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> cancelled.cancel(ADMIN, NOW)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> expired.cancel(ADMIN, EXPIRY)).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("INVITATION_NOT_PENDING");

        assertThat(accepted.status()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(cancelled.status()).isEqualTo(InvitationStatus.CANCELLED);
        assertThat(expired.status()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void expireJobMarksOnlyDuePendingInvitations() {
        Invitation invitation = operatorInvitation();
        assertThat(invitation.expire(JUST_BEFORE_EXPIRY)).isFalse();
        assertThat(invitation.status()).isEqualTo(InvitationStatus.PENDING);

        assertThat(invitation.expire(EXPIRY)).isTrue();
        assertThat(invitation.status()).isEqualTo(InvitationStatus.EXPIRED);
        assertThat(invitation.expire(EXPIRY.plusSeconds(1))).isFalse();

        Invitation cancelled = operatorInvitation();
        cancelled.cancel(ADMIN, NOW);
        assertThat(cancelled.expire(EXPIRY)).isFalse();
        assertThat(cancelled.status()).isEqualTo(InvitationStatus.CANCELLED);
    }

    @Test
    void tokensAreUnpredictableAndHiddenInToString() {
        InvitationToken first = InvitationToken.generate();
        InvitationToken second = InvitationToken.generate();

        assertThat(first.value()).isNotEqualTo(second.value()).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(first.toString()).doesNotContain(first.value());
        assertThatThrownBy(() -> new InvitationToken("corto")).isInstanceOf(IllegalArgumentException.class);
    }
}

package com.nodotextil.trazatex.organizationaccess.domain.event;

import com.nodotextil.trazatex.organizationaccess.domain.model.AdminRequest;
import com.nodotextil.trazatex.organizationaccess.domain.model.Company;
import com.nodotextil.trazatex.organizationaccess.domain.model.CompanyPrivacy;
import com.nodotextil.trazatex.organizationaccess.domain.model.CompanyStatus;
import com.nodotextil.trazatex.organizationaccess.domain.model.ConfigurableField;
import com.nodotextil.trazatex.organizationaccess.domain.model.Email;
import com.nodotextil.trazatex.organizationaccess.domain.model.Invitation;
import com.nodotextil.trazatex.organizationaccess.domain.model.InvitationToken;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import com.nodotextil.trazatex.organizationaccess.domain.model.Ruc;
import com.nodotextil.trazatex.organizationaccess.domain.model.TextileActivity;
import com.nodotextil.trazatex.organizationaccess.domain.model.User;
import com.nodotextil.trazatex.organizationaccess.domain.model.UserStatus;
import com.nodotextil.trazatex.organizationaccess.domain.model.Visibility;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationEventsTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private final UUID companyId = UUID.randomUUID();

    @Test
    void companyRegisteredCarriesTheCatalogPayload() {
        Company company = Company.register(companyId, new Ruc("20100070970"), "Hilos SAC",
                List.of(TextileActivity.WEAVING), NOW);

        CompanyRegistered event = CompanyRegistered.of(company);

        assertThat(event.type()).isEqualTo("CompanyRegistered");
        assertThat(event.aggregateId()).isEqualTo(companyId.toString());
        assertThat(event.ruc()).isEqualTo("20100070970");
        assertThat(event.businessName()).isEqualTo("Hilos SAC");
        assertThat(event.activities()).containsExactly(TextileActivity.WEAVING);
        assertThat(event.status()).isEqualTo(CompanyStatus.ACTIVE);
    }

    @Test
    void companyStatusChangedReflectsTheNewStatus() {
        Company company = Company.register(companyId, new Ruc("20100070970"), "Hilos SAC",
                List.of(TextileActivity.WEAVING), NOW);
        company.deactivate();

        CompanyStatusChanged event = CompanyStatusChanged.of(company);

        assertThat(event.type()).isEqualTo("CompanyStatusChanged");
        assertThat(event.aggregateId()).isEqualTo(companyId.toString());
        assertThat(event.status()).isEqualTo(CompanyStatus.INACTIVE);
    }

    @Test
    void companyPrivacyUpdatedCarriesTheCompleteMap() {
        CompanyPrivacy privacy = CompanyPrivacy.defaults(companyId);
        privacy.update(Map.of("ORIGIN", Visibility.SHARED));

        CompanyPrivacyUpdated event = CompanyPrivacyUpdated.of(privacy);

        assertThat(event.type()).isEqualTo("CompanyPrivacyUpdated");
        assertThat(event.aggregateId()).isEqualTo(companyId.toString());
        assertThat(event.fields()).hasSize(ConfigurableField.values().length);
        assertThat(event.fields().get(ConfigurableField.ORIGIN)).isEqualTo(Visibility.SHARED);
        assertThat(event.fields().get(ConfigurableField.SUPPLIER)).isEqualTo(Visibility.PRIVATE);
    }

    @Test
    void invitationCreatedCarriesAcceptUrlAndExpiry() {
        Invitation invitation = Invitation.issue(UUID.randomUUID(), InvitationToken.generate(), new Email("Op@Textil.pe"),
                "Luis", "Rojas", "Tejedor", Role.OPERATOR, companyId, UUID.randomUUID(), NOW);

        InvitationCreated event = InvitationCreated.of(invitation, "http://localhost:5173/invitations/abc");

        assertThat(event.type()).isEqualTo("InvitationCreated");
        assertThat(event.aggregateId()).isEqualTo(invitation.id().toString());
        assertThat(event.email()).isEqualTo("op@textil.pe");
        assertThat(event.fullName()).isEqualTo("Luis Rojas");
        assertThat(event.role()).isEqualTo(Role.OPERATOR);
        assertThat(event.companyId()).isEqualTo(companyId);
        assertThat(event.acceptUrl()).isEqualTo("http://localhost:5173/invitations/abc");
        assertThat(event.expiresAt()).isEqualTo(NOW.plusSeconds(7 * 24 * 3600));
    }

    @Test
    void invitationCreatedForTheOwnerHasNoCompanyNorNameIfNotGiven() {
        Invitation invitation = Invitation.issue(UUID.randomUUID(), InvitationToken.generate(), new Email("owner@nodotextil.pe"),
                null, null, null, Role.LICENSE_OWNER, null, null, NOW);

        InvitationCreated event = InvitationCreated.of(invitation, "url");

        assertThat(event.companyId()).isNull();
        assertThat(event.fullName()).isNull();
    }

    @Test
    void adminRequestResolvedIsKeyedByTheRequestAndCarriesTheDecision() {
        UUID requester = UUID.randomUUID();
        AdminRequest request = AdminRequest.create(UUID.randomUUID(), companyId, requester, "Marta Quispe",
                "marta@textil.pe", "Gerente", NOW);
        request.reject(UUID.randomUUID(), NOW);

        AdminRequestResolved event = AdminRequestResolved.of(request);

        assertThat(event.type()).isEqualTo("AdminRequestResolved");
        assertThat(event.aggregateId()).isEqualTo(request.id().toString());
        assertThat(event.requesterUserId()).isEqualTo(requester);
        assertThat(event.companyId()).isEqualTo(companyId);
        assertThat(event.decision().name()).isEqualTo("REJECTED");
    }

    @Test
    void aPendingRequestHasNoResolvedEvent() {
        AdminRequest request = AdminRequest.create(UUID.randomUUID(), companyId, UUID.randomUUID(), "Marta Quispe",
                "marta@textil.pe", "Gerente", NOW);
        assertThatThrownBy(() -> AdminRequestResolved.of(request)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void userAccountUpdatedCarriesTheUserSnapshot() {
        Invitation invitation = Invitation.issue(UUID.randomUUID(), InvitationToken.generate(), new Email("op@textil.pe"),
                "Luis", "Rojas", "Tejedor", Role.OPERATOR, companyId, UUID.randomUUID(), NOW);
        User user = invitation.accept(UUID.randomUUID(), "Luis", "Rojas", "hash", NOW);

        UserAccountUpdated event = UserAccountUpdated.of(user);

        assertThat(event.type()).isEqualTo("UserAccountUpdated");
        assertThat(event.aggregateId()).isEqualTo(user.id().toString());
        assertThat(event.email()).isEqualTo("op@textil.pe");
        assertThat(event.fullName()).isEqualTo("Luis Rojas");
        assertThat(event.role()).isEqualTo(Role.OPERATOR);
        assertThat(event.companyId()).isEqualTo(companyId);
        assertThat(event.status()).isEqualTo(UserStatus.ACTIVE);
    }
}

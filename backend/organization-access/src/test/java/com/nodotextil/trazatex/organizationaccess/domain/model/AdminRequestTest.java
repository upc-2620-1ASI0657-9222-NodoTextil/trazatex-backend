package com.nodotextil.trazatex.organizationaccess.domain.model;

import com.nodotextil.trazatex.sharedkernel.exception.ConflictException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminRequestTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private static final UUID OWNER = UUID.randomUUID();

    private AdminRequest request() {
        return AdminRequest.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                " Marta Quispe ", "Marta@Textil.pe", "Gerente", NOW);
    }

    @Test
    void createsPendingRequest() {
        AdminRequest request = request();

        assertThat(request.status()).isEqualTo(AdminRequestStatus.PENDING);
        assertThat(request.fullName()).isEqualTo("Marta Quispe");
        assertThat(request.email().value()).isEqualTo("marta@textil.pe");
        assertThat(request.resolvedAt()).isNull();
        assertThat(request.resolvedBy()).isNull();
    }

    @Test
    void missingDataIsListedInTheError() {
        assertThatThrownBy(() -> AdminRequest.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), " ", null, "", NOW))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo("MISSING_FIELDS");
                    assertThat(e.details()).containsExactly("fullName", "email", "position");
                });
    }

    @Test
    void approveRecordsWhoAndWhen() {
        AdminRequest request = request();
        Instant later = NOW.plusSeconds(60);

        request.approve(OWNER, later);

        assertThat(request.status()).isEqualTo(AdminRequestStatus.APPROVED);
        assertThat(request.resolvedBy()).isEqualTo(OWNER);
        assertThat(request.resolvedAt()).isEqualTo(later);
    }

    @Test
    void rejectRecordsWhoAndWhen() {
        AdminRequest request = request();

        request.reject(OWNER, NOW);

        assertThat(request.status()).isEqualTo(AdminRequestStatus.REJECTED);
        assertThat(request.resolvedBy()).isEqualTo(OWNER);
    }

    @Test
    void aResolvedRequestCannotBeResolvedAgain() {
        AdminRequest request = request();
        request.reject(OWNER, NOW);

        assertThatThrownBy(() -> request.approve(OWNER, NOW))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("ADMIN_REQUEST_NOT_PENDING");
        assertThat(request.status()).isEqualTo(AdminRequestStatus.REJECTED);
    }
}

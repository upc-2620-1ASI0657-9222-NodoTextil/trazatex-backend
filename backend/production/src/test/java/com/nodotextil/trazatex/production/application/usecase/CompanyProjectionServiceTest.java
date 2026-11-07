package com.nodotextil.trazatex.production.application.usecase;

import com.nodotextil.trazatex.production.application.port.out.CompanyProjectionRepositoryPort;
import com.nodotextil.trazatex.production.domain.model.CompanyProjection;
import com.nodotextil.trazatex.production.domain.model.CompanyStatus;
import com.nodotextil.trazatex.sharedkernel.testsupport.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyProjectionServiceTest {

    private static final UUID COMPANY = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final Instant START = Instant.parse("2026-10-20T12:00:00Z");

    private final Map<UUID, CompanyProjection> store = new HashMap<>();
    private final MutableClock clock = new MutableClock(START);
    private final CompanyProjectionService service = new CompanyProjectionService(new CompanyProjectionRepositoryPort() {
        @Override
        public void save(CompanyProjection company) {
            store.put(company.companyId(), company);
        }

        @Override
        public Optional<CompanyProjection> findById(UUID companyId) {
            return Optional.ofNullable(store.get(companyId));
        }

        @Override
        public Optional<CompanyProjection> findByIdForUpdate(UUID companyId) {
            return findById(companyId);
        }

        @Override
        public com.nodotextil.trazatex.production.application.port.out.Page<CompanyProjection> findActive(int page, int size) {
            throw new UnsupportedOperationException("no lo usa esta prueba");
        }
    }, clock);

    @Test
    void registeringANewCompanyCreatesItWithTheEventsStatus() {
        service.registered(COMPANY, "Hilos SAC", CompanyStatus.ACTIVE);

        assertThat(store.get(COMPANY).businessName()).isEqualTo("Hilos SAC");
        assertThat(store.get(COMPANY).status()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(store.get(COMPANY).updatedAt()).isEqualTo(START);
    }

    @Test
    void aStatusChangeUpdatesTheStatusAndTheDate() {
        service.registered(COMPANY, "Hilos SAC", CompanyStatus.ACTIVE);
        clock.advance(Duration.ofMinutes(5));

        service.statusChanged(COMPANY, CompanyStatus.INACTIVE);

        assertThat(store.get(COMPANY).status()).isEqualTo(CompanyStatus.INACTIVE);
        assertThat(store.get(COMPANY).updatedAt()).isEqualTo(START.plus(Duration.ofMinutes(5)));
    }

    @Test
    void repeatingTheCurrentStatusChangesNothing() {
        service.registered(COMPANY, "Hilos SAC", CompanyStatus.ACTIVE);
        clock.advance(Duration.ofMinutes(5));

        service.statusChanged(COMPANY, CompanyStatus.ACTIVE);

        assertThat(store.get(COMPANY).updatedAt()).isEqualTo(START);
    }

    @Test
    void aStatusChangeForAnUnknownCompanyCreatesItWithThatStatusAndNoName() {
        service.statusChanged(COMPANY, CompanyStatus.INACTIVE);

        assertThat(store.get(COMPANY).status()).isEqualTo(CompanyStatus.INACTIVE);
        assertThat(store.get(COMPANY).businessName()).isNull();
        assertThat(store.get(COMPANY).isActive()).isFalse();
    }

    @Test
    void aLateRegistrationCompletesTheNameButNeverOverridesTheStatus() {
        service.statusChanged(COMPANY, CompanyStatus.INACTIVE);

        service.registered(COMPANY, "Hilos SAC", CompanyStatus.ACTIVE);

        assertThat(store.get(COMPANY).businessName()).isEqualTo("Hilos SAC");
        assertThat(store.get(COMPANY).status()).isEqualTo(CompanyStatus.INACTIVE);
    }

    @Test
    void registeringAnAlreadyKnownCompanyWithTheSameNameChangesNothing() {
        service.registered(COMPANY, "Hilos SAC", CompanyStatus.ACTIVE);
        clock.advance(Duration.ofMinutes(5));

        service.registered(COMPANY, "Hilos SAC", CompanyStatus.ACTIVE);

        assertThat(store.get(COMPANY).updatedAt()).isEqualTo(START);
    }
}

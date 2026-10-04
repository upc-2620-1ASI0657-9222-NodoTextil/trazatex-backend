package com.nodotextil.trazatex.production.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.BatchSearchCriteria;
import com.nodotextil.trazatex.production.domain.CompositionComponent;
import com.nodotextil.trazatex.production.domain.InvalidBatchException;
import com.nodotextil.trazatex.production.domain.MaterialType;
import com.nodotextil.trazatex.production.domain.OperationalPhase;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SearchBatchesUseCaseTest {

    private static final UUID COMPANY_ID =
            UUID.fromString("597d5889-87bd-4f74-ae2e-81a568812302");

    private InMemoryBatchRepository repository;
    private SearchBatchesUseCase useCase;
    private Batch first;
    private Batch second;
    private Batch third;

    @BeforeEach
    void setUp() {
        repository = new InMemoryBatchRepository();
        useCase = new SearchBatchesUseCase(repository);
        first = register("2026-01-10T10:00:00Z", MaterialType.FIBER);
        second = register("2026-01-15T10:00:00Z", MaterialType.YARN);
        third = register("2026-01-20T10:00:00Z", MaterialType.FIBER);
        second.markAsSplit();
        repository.save(second);
    }

    @Test
    void returnsAllBatchesWithoutFilters() {
        List<Batch> result = useCase.execute(criteria(null, null, null, null, null));

        assertThat(result).extracting(Batch::id)
                .containsExactly(first.id(), second.id(), third.id());
    }

    @Test
    void filtersByTraceabilityId() {
        List<Batch> result = useCase.execute(
                criteria(second.traceabilityId(), null, null, null, null));

        assertThat(result).extracting(Batch::id).containsExactly(second.id());
    }

    @Test
    void combinesMaterialTypeAndOperationalPhaseFilters() {
        List<Batch> result = useCase.execute(criteria(
                null,
                MaterialType.FIBER,
                OperationalPhase.AVAILABLE,
                null,
                null));

        assertThat(result).extracting(Batch::id).containsExactly(first.id(), third.id());
    }

    @Test
    void combinesInclusiveRegistrationDateFilters() {
        LocalDateTime from = LocalDateTime.of(2026, 1, 15, 10, 0);
        LocalDateTime to = LocalDateTime.of(2026, 1, 20, 10, 0);

        List<Batch> result = useCase.execute(criteria(null, null, null, from, to));

        assertThat(result).extracting(Batch::id).containsExactly(second.id(), third.id());
    }

    @Test
    void combinesAllAvailableFilters() {
        LocalDateTime from = LocalDateTime.of(2026, 1, 18, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 1, 22, 0, 0);

        List<Batch> result = useCase.execute(criteria(
                third.traceabilityId(),
                MaterialType.FIBER,
                OperationalPhase.AVAILABLE,
                from,
                to));

        assertThat(result).extracting(Batch::id).containsExactly(third.id());
    }

    @Test
    void rejectsInvertedRegistrationDateRange() {
        LocalDateTime from = LocalDateTime.of(2026, 1, 20, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 1, 10, 0, 0);

        assertThatThrownBy(() -> criteria(null, null, null, from, to))
                .isInstanceOf(InvalidBatchException.class)
                .hasMessageContaining("cannot be after");
    }

    private Batch register(String instant, MaterialType materialType) {
        RegisterBatchUseCase registerUseCase = new RegisterBatchUseCase(
                repository,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
        return registerUseCase.execute(new RegisterBatchUseCase.Command(
                COMPANY_ID,
                "Supplier",
                "Lima, Peru",
                materialType,
                new BigDecimal("10"),
                List.of(new CompositionComponent("Cotton", new BigDecimal("100"))),
                "Accepted"));
    }

    private BatchSearchCriteria criteria(
            String traceabilityId,
            MaterialType materialType,
            OperationalPhase operationalPhase,
            LocalDateTime registeredFrom,
            LocalDateTime registeredTo) {
        return new BatchSearchCriteria(
                traceabilityId,
                materialType,
                operationalPhase,
                registeredFrom,
                registeredTo);
    }
}

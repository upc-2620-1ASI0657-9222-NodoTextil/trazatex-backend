package com.nodotextil.trazatex.production.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.CompositionComponent;
import com.nodotextil.trazatex.production.domain.InvalidBatchException;
import com.nodotextil.trazatex.production.domain.MaterialType;
import com.nodotextil.trazatex.production.domain.OperationalPhase;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SplitBatchUseCaseTest {

    private static final Instant SPLIT_INSTANT = Instant.parse("2026-03-20T14:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(SPLIT_INSTANT, ZoneOffset.UTC);

    private InMemoryBatchRepository repository;
    private TestBatchEventPublisher eventPublisher;
    private SplitBatchUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new InMemoryBatchRepository();
        eventPublisher = new TestBatchEventPublisher();
        useCase = new SplitBatchUseCase(repository, eventPublisher, FIXED_CLOCK);
    }

    @Test
    void splitsAvailableBatchCompletelyAndPublishesEvent() {
        Batch parent = registerParent();

        SplitBatchUseCase.Result result = useCase.execute(
                parent.id(), List.of(new BigDecimal("40"), new BigDecimal("60")));

        assertThat(result.parent().operationalPhase()).isEqualTo(OperationalPhase.SPLIT);
        assertThat(result.children()).hasSize(2);
        assertThat(result.children())
                .extracting(Batch::quantityKg)
                .containsExactly(new BigDecimal("40"), new BigDecimal("60"));
        assertThat(result.children().stream()
                .map(Batch::quantityKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(parent.quantityKg());

        assertThat(result.children()).allSatisfy(child -> {
            assertThat(child.operationalPhase()).isEqualTo(OperationalPhase.AVAILABLE);
            assertThat(child.responsibleCompanyId()).isEqualTo(parent.responsibleCompanyId());
            assertThat(child.supplierName()).isEqualTo(parent.supplierName());
            assertThat(child.geographicOrigin()).isEqualTo(parent.geographicOrigin());
            assertThat(child.materialType()).isEqualTo(parent.materialType());
            assertThat(child.composition()).isEqualTo(parent.composition());
            assertThat(child.receptionCharacteristics())
                    .isEqualTo(parent.receptionCharacteristics());
            assertThat(child.id()).isNotEqualTo(parent.id());
            assertThat(child.traceabilityId()).isNotEqualTo(parent.traceabilityId());
            assertThat(child.qrCode()).isNotEqualTo(parent.qrCode());
        });
        assertThat(new HashSet<>(result.children().stream().map(Batch::id).toList())).hasSize(2);
        assertThat(new HashSet<>(
                result.children().stream().map(Batch::traceabilityId).toList())).hasSize(2);
        assertThat(new HashSet<>(
                result.children().stream().map(Batch::qrCode).toList())).hasSize(2);

        assertThat(eventPublisher.splitEvents()).singleElement().satisfies(event -> {
            assertThat(event.parentBatchId()).isEqualTo(parent.id());
            assertThat(event.childBatchIds())
                    .containsExactlyElementsOf(result.children().stream().map(Batch::id).toList());
            assertThat(event.occurredAt())
                    .isEqualTo(LocalDateTime.ofInstant(SPLIT_INSTANT, ZoneOffset.UTC));
        });
    }

    @Test
    void rejectsSplitWithFewerThanTwoChildren() {
        Batch parent = registerParent();

        assertThatThrownBy(() -> useCase.execute(
                parent.id(), List.of(new BigDecimal("100"))))
                .isInstanceOf(InvalidBatchException.class)
                .hasMessageContaining("at least two");

        assertUnchanged(parent);
    }

    @Test
    void rejectsNonPositiveChildQuantity() {
        Batch parent = registerParent();

        assertThatThrownBy(() -> useCase.execute(
                parent.id(), List.of(new BigDecimal("100"), BigDecimal.ZERO)))
                .isInstanceOf(InvalidBatchException.class)
                .hasMessageContaining("greater than zero");

        assertUnchanged(parent);
    }

    @Test
    void rejectsSplitWhenChildTotalDoesNotMatchParent() {
        Batch parent = registerParent();

        assertThatThrownBy(() -> useCase.execute(
                parent.id(), List.of(new BigDecimal("30"), new BigDecimal("60"))))
                .isInstanceOf(InvalidBatchException.class)
                .hasMessageContaining("exactly");

        assertUnchanged(parent);
    }

    @Test
    void rejectsParentThatIsNotAvailable() {
        Batch parent = registerParent();
        parent.markAsSplit();
        repository.save(parent);

        assertThatThrownBy(() -> useCase.execute(
                parent.id(), List.of(new BigDecimal("50"), new BigDecimal("50"))))
                .isInstanceOf(InvalidBatchException.class)
                .hasMessageContaining("AVAILABLE");

        assertThat(eventPublisher.splitEvents()).isEmpty();
        assertThat(repository.size()).isEqualTo(1);
    }

    @Test
    void rejectsOperationallyBlockedParent() {
        Batch parent = registerParent();
        parent.block();
        repository.save(parent);

        assertThatThrownBy(() -> useCase.execute(
                parent.id(), List.of(new BigDecimal("50"), new BigDecimal("50"))))
                .isInstanceOf(InvalidBatchException.class)
                .hasMessageContaining("unblocked");

        assertThat(parent.operationalPhase()).isEqualTo(OperationalPhase.AVAILABLE);
        assertThat(parent.operationallyBlocked()).isTrue();
        assertThat(eventPublisher.splitEvents()).isEmpty();
        assertThat(repository.size()).isEqualTo(1);
    }

    private Batch registerParent() {
        RegisterBatchUseCase registerUseCase = new RegisterBatchUseCase(repository, FIXED_CLOCK);
        return registerUseCase.execute(new RegisterBatchUseCase.Command(
                UUID.fromString("b0f78df0-3a50-4b52-8595-b681b32c6c07"),
                "Andean Fibers",
                "Cusco, Peru",
                MaterialType.FIBER,
                new BigDecimal("100"),
                List.of(
                        new CompositionComponent("Alpaca", new BigDecimal("80")),
                        new CompositionComponent("Wool", new BigDecimal("20"))),
                "Sorted and dry"));
    }

    private void assertUnchanged(Batch parent) {
        assertThat(parent.operationalPhase()).isEqualTo(OperationalPhase.AVAILABLE);
        assertThat(repository.size()).isEqualTo(1);
        assertThat(eventPublisher.splitEvents()).isEmpty();
    }
}

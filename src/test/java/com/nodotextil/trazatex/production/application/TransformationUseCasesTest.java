package com.nodotextil.trazatex.production.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.CompositionComponent;
import com.nodotextil.trazatex.production.domain.InvalidTransformationException;
import com.nodotextil.trazatex.production.domain.Machine;
import com.nodotextil.trazatex.production.domain.MachineRepository;
import com.nodotextil.trazatex.production.domain.MachineStatus;
import com.nodotextil.trazatex.production.domain.MaterialType;
import com.nodotextil.trazatex.production.domain.OperationalPhase;
import com.nodotextil.trazatex.production.domain.Transformation;
import com.nodotextil.trazatex.production.domain.TransformationRepository;
import com.nodotextil.trazatex.production.domain.TransformationType;
import com.nodotextil.trazatex.production.domain.strategy.CuttingTransformationStrategy;
import com.nodotextil.trazatex.production.domain.strategy.DyeingTransformationStrategy;
import com.nodotextil.trazatex.production.domain.strategy.FinishingTransformationStrategy;
import com.nodotextil.trazatex.production.domain.strategy.GarmentingTransformationStrategy;
import com.nodotextil.trazatex.production.domain.strategy.SpinningTransformationStrategy;
import com.nodotextil.trazatex.production.domain.strategy.TransformationStrategyFactory;
import com.nodotextil.trazatex.production.domain.strategy.WeavingTransformationStrategy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransformationUseCasesTest {

    private static final Instant TRANSFORMATION_INSTANT =
            Instant.parse("2026-04-10T12:00:00Z");
    private static final Clock FIXED_CLOCK =
            Clock.fixed(TRANSFORMATION_INSTANT, ZoneOffset.UTC);
    private static final UUID COMPANY_ID =
            UUID.fromString("f1418935-54ba-4554-ae3e-783a569542e1");
    private static final UUID OPERATOR_ID =
            UUID.fromString("3a42fac8-bba9-47f7-a4f7-aad23af090bb");

    private InMemoryBatchRepository batchRepository;
    private InMemoryMachineRepository machineRepository;
    private InMemoryTransformationRepository transformationRepository;
    private TestTransformationEventPublisher eventPublisher;
    private Machine machine;

    @BeforeEach
    void setUp() {
        batchRepository = new InMemoryBatchRepository();
        machineRepository = new InMemoryMachineRepository();
        transformationRepository = new InMemoryTransformationRepository();
        eventPublisher = new TestTransformationEventPublisher();
        machine = Machine.register(
                UUID.randomUUID(), "M-100", "Spinning line", "SPINNER", COMPANY_ID);
        machineRepository.save(machine);
    }

    @Test
    void startsTransformation() {
        Batch input = createInput();

        Transformation transformation = start(List.of(input.id()));

        assertThat(transformation.id()).isNotNull();
        assertThat(transformation.companyId()).isEqualTo(COMPANY_ID);
        assertThat(transformation.operatorId()).isEqualTo(OPERATOR_ID);
        assertThat(transformation.machineId()).isEqualTo(machine.id());
        assertThat(transformation.type()).isEqualTo(TransformationType.SPINNING);
        assertThat(transformation.inputBatchIds()).containsExactly(input.id());
        assertThat(transformation.outputBatchIds()).isEmpty();
        assertThat(transformation.startedAt())
                .isEqualTo(LocalDateTime.ofInstant(TRANSFORMATION_INSTANT, ZoneOffset.UTC));
        assertThat(transformationRepository.findById(transformation.id()))
                .contains(transformation);
    }

    @Test
    void rejectsStartWithMachineThatIsNotActive() {
        machine.changeStatus(MachineStatus.INACTIVE);
        Batch input = createInput();

        assertThatThrownBy(() -> start(List.of(input.id())))
                .isInstanceOf(InvalidTransformationException.class)
                .hasMessageContaining("ACTIVE");

        assertThat(input.operationalPhase()).isEqualTo(OperationalPhase.AVAILABLE);
        assertThat(transformationRepository.size()).isZero();
    }

    @Test
    void rejectsStartWithBatchThatIsNotAvailable() {
        Batch input = createInput();
        input.markAsSplit();
        batchRepository.save(input);

        assertThatThrownBy(() -> start(List.of(input.id())))
                .isInstanceOf(InvalidTransformationException.class)
                .hasMessageContaining("AVAILABLE");

        assertThat(transformationRepository.size()).isZero();
    }

    @Test
    void movesInputsFromAvailableToInTransformation() {
        Batch input = createInput();

        start(List.of(input.id()));

        assertThat(batchRepository.findById(input.id()).orElseThrow().operationalPhase())
                .isEqualTo(OperationalPhase.IN_TRANSFORMATION);
    }

    @Test
    void completesTransformationAndPublishesEvent() {
        Batch input = createInput();
        Transformation transformation = start(List.of(input.id()));

        CompleteTransformationUseCase.Result result = complete(transformation);

        assertThat(result.transformation().completedAt())
                .isEqualTo(LocalDateTime.ofInstant(TRANSFORMATION_INSTANT, ZoneOffset.UTC));
        assertThat(result.transformation().outputBatchIds())
                .containsExactlyElementsOf(result.outputs().stream().map(Batch::id).toList());
        assertThat(eventPublisher.completedEvents()).singleElement().satisfies(event -> {
            assertThat(event.inputBatchIds()).containsExactly(input.id());
            assertThat(event.outputBatchIds())
                    .containsExactlyElementsOf(result.outputs().stream().map(Batch::id).toList());
            assertThat(event.occurredAt())
                    .isEqualTo(LocalDateTime.ofInstant(TRANSFORMATION_INSTANT, ZoneOffset.UTC));
        });
    }

    @Test
    void rejectsCompletionWhenInputIsNotInTransformation() {
        Batch input = createInput();
        Transformation transformation = Transformation.start(
                UUID.randomUUID(),
                COMPANY_ID,
                OPERATOR_ID,
                machine.id(),
                TransformationType.SPINNING,
                List.of(input.id()),
                LocalDateTime.now(FIXED_CLOCK),
                input.quantityKg());
        transformationRepository.save(transformation);

        assertThatThrownBy(() -> complete(transformation))
                .isInstanceOf(InvalidTransformationException.class)
                .hasMessageContaining("IN_TRANSFORMATION");

        assertThat(transformation.isCompleted()).isFalse();
        assertThat(eventPublisher.completedEvents()).isEmpty();
    }

    @Test
    void movesInputsFromInTransformationToProcessed() {
        Batch input = createInput();
        Transformation transformation = start(List.of(input.id()));

        complete(transformation);

        assertThat(batchRepository.findById(input.id()).orElseThrow().operationalPhase())
                .isEqualTo(OperationalPhase.PROCESSED);
    }

    @Test
    void createsAvailableOutputsWithNewIdentifiers() {
        Batch input = createInput();
        Transformation transformation = start(List.of(input.id()));

        Batch output = complete(transformation).outputs().getFirst();

        assertThat(output.operationalPhase()).isEqualTo(OperationalPhase.AVAILABLE);
        assertThat(output.id()).isNotEqualTo(input.id());
        assertThat(output.traceabilityId()).startsWith("TRZ-")
                .isNotEqualTo(input.traceabilityId());
        assertThat(output.qrCode()).startsWith("QR-").isNotEqualTo(input.qrCode());
        assertThat(output.composition()).isEqualTo(input.composition());
    }

    @Test
    void recordsValidTransformationBalance() {
        Batch input = createInput();
        Transformation transformation = start(List.of(input.id()));

        CompleteTransformationUseCase.Result result = complete(
                transformation, new BigDecimal("90"), new BigDecimal("5"), "Cutting waste");

        Transformation completed = result.transformation();
        assertThat(completed.totalInputKg()).isEqualByComparingTo("100");
        assertThat(completed.totalOutputKg()).isEqualByComparingTo("90");
        assertThat(completed.wasteKg()).isEqualByComparingTo("5");
        assertThat(completed.wasteReason()).isEqualTo("Cutting waste");
        assertThat(completed.shrinkageKg()).isEqualByComparingTo("5");
        assertThat(completed.totalOutputKg()
                .add(completed.shrinkageKg())
                .add(completed.wasteKg()))
                .isEqualByComparingTo(completed.totalInputKg());
    }

    @Test
    void rejectsNegativeTransformationBalance() {
        Batch input = createInput();
        Transformation transformation = start(List.of(input.id()));

        assertThatThrownBy(() -> complete(
                transformation, new BigDecimal("96"), new BigDecimal("5"), "Cutting waste"))
                .isInstanceOf(InvalidTransformationException.class)
                .hasMessageContaining("cannot exceed");

        assertThat(input.operationalPhase()).isEqualTo(OperationalPhase.IN_TRANSFORMATION);
        assertThat(transformation.isCompleted()).isFalse();
    }

    @Test
    void rejectsWasteWithoutReason() {
        Batch input = createInput();
        Transformation transformation = start(List.of(input.id()));

        assertThatThrownBy(() -> complete(
                transformation, new BigDecimal("90"), new BigDecimal("5"), null))
                .isInstanceOf(InvalidTransformationException.class)
                .hasMessageContaining("reason");

        assertThat(input.operationalPhase()).isEqualTo(OperationalPhase.IN_TRANSFORMATION);
        assertThat(transformation.isCompleted()).isFalse();
    }

    private Batch createInput() {
        RegisterBatchUseCase registerUseCase =
                new RegisterBatchUseCase(batchRepository, FIXED_CLOCK);
        return registerUseCase.execute(new RegisterBatchUseCase.Command(
                COMPANY_ID,
                "Supplier",
                "Lima, Peru",
                MaterialType.FIBER,
                new BigDecimal("100"),
                List.of(new CompositionComponent("Cotton", new BigDecimal("100"))),
                "Accepted"));
    }

    private Transformation start(List<UUID> inputBatchIds) {
        return new StartTransformationUseCase(
                transformationRepository, batchRepository, machineRepository, FIXED_CLOCK)
                .execute(new StartTransformationUseCase.Command(
                        COMPANY_ID,
                        OPERATOR_ID,
                        machine.id(),
                        TransformationType.SPINNING,
                        inputBatchIds));
    }

    private CompleteTransformationUseCase.Result complete(Transformation transformation) {
        return complete(transformation, new BigDecimal("90"), BigDecimal.ZERO, null);
    }

    private CompleteTransformationUseCase.Result complete(
            Transformation transformation,
            BigDecimal outputQuantityKg,
            BigDecimal wasteKg,
            String wasteReason) {
        CompleteTransformationUseCase useCase = new CompleteTransformationUseCase(
                transformationRepository,
                batchRepository,
                eventPublisher,
                strategyFactory(),
                FIXED_CLOCK);
        return useCase.execute(
                transformation.id(),
                new CompleteTransformationUseCase.Command(List.of(
                        new CompleteTransformationUseCase.OutputCommand(
                                outputQuantityKg,
                                MaterialType.YARN,
                                "Lima, Peru",
                                "Transformation output")),
                        wasteKg,
                        wasteReason));
    }

    private TransformationStrategyFactory strategyFactory() {
        return new TransformationStrategyFactory(List.of(
                new SpinningTransformationStrategy(),
                new WeavingTransformationStrategy(),
                new DyeingTransformationStrategy(),
                new FinishingTransformationStrategy(),
                new CuttingTransformationStrategy(),
                new GarmentingTransformationStrategy()));
    }

    private static final class InMemoryMachineRepository implements MachineRepository {

        private final Map<UUID, Machine> machines = new HashMap<>();

        @Override
        public Machine save(Machine machine) {
            machines.put(machine.id(), machine);
            return machine;
        }

        @Override
        public Optional<Machine> findById(UUID id) {
            return Optional.ofNullable(machines.get(id));
        }
    }

    private static final class InMemoryTransformationRepository
            implements TransformationRepository {

        private final Map<UUID, Transformation> transformations = new HashMap<>();

        @Override
        public Transformation save(Transformation transformation) {
            transformations.put(transformation.id(), transformation);
            return transformation;
        }

        @Override
        public Optional<Transformation> findById(UUID id) {
            return Optional.ofNullable(transformations.get(id));
        }

        int size() {
            return transformations.size();
        }
    }
}

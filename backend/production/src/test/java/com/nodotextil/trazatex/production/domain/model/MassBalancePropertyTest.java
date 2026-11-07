package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.production.domain.service.DyeingStrategy;
import com.nodotextil.trazatex.production.domain.service.SpinningStrategy;
import com.nodotextil.trazatex.production.domain.service.TransformationFactory;
import com.nodotextil.trazatex.production.domain.service.WeavingStrategy;
import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.nodotextil.trazatex.production.domain.Fixtures.COMPANY;
import static com.nodotextil.trazatex.production.domain.Fixtures.NOW;
import static com.nodotextil.trazatex.production.domain.Fixtures.OPERATOR;
import static com.nodotextil.trazatex.production.domain.Fixtures.activeMachine;
import static com.nodotextil.trazatex.production.domain.Fixtures.availableBatch;
import static com.nodotextil.trazatex.production.domain.Fixtures.cotton;
import static com.nodotextil.trazatex.production.domain.Fixtures.factory;
import static com.nodotextil.trazatex.production.domain.Fixtures.grams;
import static com.nodotextil.trazatex.production.domain.Fixtures.transformationFactory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Invariante de balance de masa (ADR-003): {@code merma = entrada − salida − desperdicio}, y solo se finaliza si es ≥ 0.
 * Propiedades con casos generados al azar con semilla fija (≥ 1000 por invariante) y la prueba de que ninguna
 * estrategia —ni una permisiva— puede relajarla.
 */
class MassBalancePropertyTest {

    private static final long SEED = 20261020L;
    private static final int CASES = 1500;

    /** Estrategia que acepta todo: aísla la invariante de las reglas propias de un tipo. */
    private static final class PermissiveStrategy implements TransformationStrategy {
        private final List<BalanceSummary> received = new ArrayList<>();

        @Override
        public TransformationType type() {
            return TransformationType.SPINNING;
        }

        @Override
        public void validate(BalanceSummary balance) {
            received.add(balance);
        }
    }

    /** Un caso: gramos de entrada por lote, de salida por lote y de desperdicio. */
    record Case(List<Long> inputs, List<Long> outputs, long waste) {
        long totalInput() {
            return inputs.stream().mapToLong(Long::longValue).sum();
        }

        long totalOutput() {
            return outputs.stream().mapToLong(Long::longValue).sum();
        }

        boolean isBalanced() {
            return totalOutput() + waste <= totalInput();
        }
    }

    private record Setup(Transformation transformation, List<Batch> inputs, PermissiveStrategy strategy) {
    }

    private static Setup start(Case c, long seed) {
        Random random = new Random(seed);
        PermissiveStrategy strategy = new PermissiveStrategy();
        List<Batch> inputs = new ArrayList<>();
        for (long grams : c.inputs()) {
            inputs.add(availableBatch(grams(grams), cotton(), random));
        }
        Transformation transformation = Transformation.start(UUID.randomUUID(), strategy, COMPANY, activeMachine(), OPERATOR, inputs, NOW);
        return new Setup(transformation, inputs, strategy);
    }

    private static List<BatchFactory.OutputSpec> outputs(Case c) {
        return c.outputs().stream().map(g -> new BatchFactory.OutputSpec(null, "Salida", grams(g), false, null)).toList();
    }

    private static Quantity waste(long grams) {
        return Quantity.nonNegative(BigDecimal.valueOf(grams, 3));
    }

    /** Propiedad 1: se finaliza si y solo si salida + desperdicio ≤ entrada, y entonces merma = entrada − salida − desperdicio. */
    @ParameterizedTest(name = "caso {index}")
    @MethodSource("randomCases")
    void finalizesIffTheBalanceIsNonNegativeAndTheShrinkageIsExact(Case c) {
        Setup setup = start(c, c.totalInput());
        Transformation transformation = setup.transformation();
        String reason = c.waste() > 0 ? "Motivo" : null;

        if (c.isBalanced()) {
            List<Batch> produced = transformation.finalize(setup.inputs(), outputs(c), waste(c.waste()), reason, factory(c.totalInput()), OPERATOR, NOW);

            long shrinkage = c.totalInput() - c.totalOutput() - c.waste();
            assertThat(transformation.shrinkage()).isEqualTo(Quantity.nonNegative(BigDecimal.valueOf(shrinkage, 3)));
            assertThat(transformation.shrinkage().kg().signum()).isGreaterThanOrEqualTo(0);
            // balance de masa cerrado: entrada = salida + desperdicio + merma
            assertThat(transformation.totalOutput().plus(transformation.waste()).plus(transformation.shrinkage()))
                    .isEqualTo(transformation.totalInput());
            assertThat(transformation.totalInput()).isEqualTo(grams(c.totalInput()));
            assertThat(transformation.totalOutput()).isEqualTo(grams(c.totalOutput()));
            assertThat(transformation.status()).isEqualTo(TransformationStatus.COMPLETED);
            assertThat(setup.inputs()).allSatisfy(input -> assertThat(input.phase()).isEqualTo(BatchPhase.PROCESSED));
            assertThat(produced).hasSameSizeAs(c.outputs()).allSatisfy(out -> assertThat(out.phase()).isEqualTo(BatchPhase.AVAILABLE));
            Quantity producedTotal = produced.stream().map(Batch::quantity).reduce(Quantity.ZERO, Quantity::plus);
            assertThat(producedTotal).as("la masa de los lotes creados es la salida declarada").isEqualTo(transformation.totalOutput());
        } else {
            assertThatThrownBy(() -> transformation.finalize(setup.inputs(), outputs(c), waste(c.waste()), reason, factory(1), OPERATOR, NOW))
                    .isInstanceOfSatisfying(TrazatexException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.INVALID_QUANTITIES));
            assertThat(transformation.status()).isEqualTo(TransformationStatus.IN_PROGRESS);
            assertThat(transformation.shrinkage()).isNull();
            assertThat(setup.inputs()).allSatisfy(input -> assertThat(input.phase()).isEqualTo(BatchPhase.IN_TRANSFORMATION));
            assertThat(setup.strategy().received).as("la estrategia nunca ve un balance inválido").isEmpty();
        }
    }

    /** Propiedad 2: la estrategia recibe siempre un balance ya válido (merma ≥ 0 y cerrado), inmutable. */
    @ParameterizedTest(name = "caso {index}")
    @MethodSource("balancedCases")
    void theStrategyOnlySeesAValidBalance(Case c) {
        Setup setup = start(c, c.totalInput() + 7);

        setup.transformation().finalize(setup.inputs(), outputs(c), waste(c.waste()), c.waste() > 0 ? "Motivo" : null, factory(2), OPERATOR, NOW);

        assertThat(setup.strategy().received).hasSize(1);
        BalanceSummary seen = setup.strategy().received.get(0);
        assertThat(seen.shrinkage().kg().signum()).isGreaterThanOrEqualTo(0);
        assertThat(seen.totalOutput().plus(seen.waste()).plus(seen.shrinkage())).isEqualTo(seen.totalInput());
        assertThat(seen.inputCount()).isEqualTo(c.inputs().size());
        assertThat(seen.outputCount()).isEqualTo(c.outputs().size());
    }

    /**
     * Propiedad 3 (la que pide el plan): <b>ninguna estrategia relaja la invariante</b>. Con salida + desperdicio >
     * entrada, SPINNING, WEAVING, DYEING y una estrategia permisiva rechazan igual con {@code INVALID_QUANTITIES} y
     * nunca llegan a {@code YIELD_BELOW_MINIMUM} ni a finalizar, aunque su rendimiento propio fuese alto.
     */
    @ParameterizedTest(name = "{0}")
    @EnumSource(TransformationType.class)
    void noStrategyRelaxesTheMassBalance(TransformationType type) {
        TransformationFactory transformations = transformationFactory();
        Random random = new Random(SEED + type.ordinal());
        for (int i = 0; i < CASES; i++) {
            long input = 1 + random.nextInt(2_000_000);
            // desperdicio + salida excede la entrada, por 1 g hasta por el doble
            long excess = 1 + (random.nextInt(4) == 0 ? random.nextInt(2_000_000) : random.nextInt(5));
            long output = 1 + random.nextInt((int) input);
            long waste = input - output + excess;
            Case c = new Case(List.of(input), List.of(output), waste);
            assertThat(c.isBalanced()).isFalse();

            Batch batch = availableBatch(grams(input), cotton(), random);
            Transformation transformation = transformations.start(UUID.randomUUID(), type, COMPANY, activeMachine(), OPERATOR, List.of(batch), NOW);

            assertThatThrownBy(() -> transformation.finalize(List.of(batch), outputs(c), waste(waste), "Motivo", factory(3), OPERATOR, NOW))
                    .as("%s, entrada=%d salida=%d desperdicio=%d", type, input, output, waste)
                    .isInstanceOfSatisfying(TrazatexException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.INVALID_QUANTITIES));
            assertThat(transformation.status()).isEqualTo(TransformationStatus.IN_PROGRESS);
            assertThat(batch.phase()).isEqualTo(BatchPhase.IN_TRANSFORMATION);
        }
    }

    /** Propiedad 4: la fórmula de la merma es la misma con cualquier estrategia real que acepte el caso. */
    @ParameterizedTest(name = "{0}")
    @EnumSource(TransformationType.class)
    void everyStrategyUsesTheSameShrinkageFormula(TransformationType type) {
        TransformationFactory transformations = transformationFactory();
        Random random = new Random(SEED + 100 + type.ordinal());
        int accepted = 0;
        for (int i = 0; i < CASES; i++) {
            long input = 1000 + random.nextInt(2_000_000);
            long minimumOutput = (input * 80 + 99) / 100;                           // techo de 80 %: lo exigen WEAVING y DYEING
            long output = minimumOutput + random.nextInt((int) (input - minimumOutput + 1));
            long waste = random.nextInt((int) Math.max(1, input - output + 1));
            Case c = new Case(List.of(input), List.of(output), waste);
            if (!c.isBalanced() || output == 0) {
                continue;
            }
            Batch batch = availableBatch(grams(input), cotton(), random);
            Transformation transformation = transformations.start(UUID.randomUUID(), type, COMPANY, activeMachine(), OPERATOR, List.of(batch), NOW);

            transformation.finalize(List.of(batch), outputs(c), waste(waste), waste > 0 ? "Motivo" : null, factory(4), OPERATOR, NOW);

            assertThat(transformation.shrinkage()).isEqualTo(Quantity.nonNegative(BigDecimal.valueOf(input - output - waste, 3)));
            accepted++;
        }
        assertThat(accepted).as("casos aceptados del tipo %s", type).isGreaterThanOrEqualTo(1000);
    }

    /** Propiedad 5: las cantidades con más de 3 decimales no entran, así que la aritmética nunca pierde exactitud. */
    @Test
    void sumsOfThreeDecimalQuantitiesAreAlwaysExact() {
        Random random = new Random(SEED);
        for (int i = 0; i < CASES; i++) {
            long a = 1 + random.nextInt(1_000_000_000);
            long b = 1 + random.nextInt(1_000_000_000);
            assertThat(grams(a).plus(grams(b))).isEqualTo(grams(a + b));
            assertThat(grams(a + b).minus(grams(b))).isEqualByComparingTo(BigDecimal.valueOf(a, 3));
        }
    }

    // --- generadores --------------------------------------------------------------------------------------------

    static Stream<Case> randomCases() {
        Random random = new Random(SEED);
        return IntStream.range(0, CASES).mapToObj(i -> {
            List<Long> inputs = randomParts(random, 1 + random.nextInt(4), 1_000_000);
            long totalInput = inputs.stream().mapToLong(Long::longValue).sum();
            List<Long> outputs;
            long waste;
            switch (i % 4) {
                case 0 -> {            // alrededor del límite: salida + desperdicio = entrada ± 1 g
                    long target = Math.max(2, totalInput + random.nextInt(3) - 1);
                    waste = random.nextBoolean() ? 0 : random.nextInt((int) Math.max(1, target / 2));
                    outputs = splitExactly(random, target - waste);
                }
                case 1 -> {            // balanceado holgado
                    outputs = randomParts(random, 1 + random.nextInt(3), (int) Math.max(1, totalInput / 4));
                    waste = random.nextInt((int) Math.max(1, totalInput / 4));
                }
                case 2 -> {            // la salida sola ya excede
                    outputs = randomParts(random, 1 + random.nextInt(3), (int) Math.max(2, totalInput));
                    waste = random.nextInt(3);
                }
                default -> {           // totalmente al azar
                    outputs = randomParts(random, 1 + random.nextInt(4), 1_000_000);
                    waste = random.nextInt(1_000_000);
                }
            }
            return new Case(inputs, outputs, waste);
        });
    }

    static Stream<Case> balancedCases() {
        return randomCases().filter(Case::isBalanced);
    }

    private static List<Long> randomParts(Random random, int count, int max) {
        List<Long> parts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            parts.add(1L + random.nextInt(Math.max(1, max)));
        }
        return parts;
    }

    /** Reparte {@code total} (≥ 1) en 1 a 3 partes positivas que suman exactamente {@code total}. */
    private static List<Long> splitExactly(Random random, long total) {
        total = Math.max(1, total);
        int parts = (int) Math.min(total, 1 + random.nextInt(3));
        List<Long> result = new ArrayList<>();
        long remaining = total;
        for (int i = 0; i < parts - 1; i++) {
            long part = 1 + (long) (random.nextDouble() * (remaining - (parts - 1 - i)));
            result.add(part);
            remaining -= part;
        }
        result.add(remaining);
        return result;
    }

    @Test
    void theRealStrategiesAreWiredForEveryType() {
        TransformationFactory transformations = new TransformationFactory(List.of(
                new SpinningStrategy(new BigDecimal("60")), new WeavingStrategy(new BigDecimal("80")),
                new DyeingStrategy(new BigDecimal("80"))));
        for (TransformationType type : TransformationType.values()) {
            assertThat(transformations.strategyFor(type).type()).isEqualTo(type);
        }
    }
}

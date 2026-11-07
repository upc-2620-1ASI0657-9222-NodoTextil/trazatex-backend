package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.nodotextil.trazatex.production.domain.Fixtures.composition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositionTest {

    /** Semilla fija: los casos son aleatorios pero idénticos en cada corrida, así que cualquier fallo se reproduce. */
    private static final long SEED = 20261020L;
    private static final int CASES = 1500;
    private static final String[] MATERIALS = {"Algodón", "Poliéster", "Lana", "Lino", "Viscosa", "Elastano"};

    // --- validez ------------------------------------------------------------------------------------------------

    @Test
    void acceptsComponentsThatSumExactly100() {
        Composition composition = composition("Algodón", "60.5", "Poliéster", "39.5");
        assertThat(composition.components()).hasSize(2);
        assertThat(composition.components().get(0).percentage().scale()).isEqualTo(2);
    }

    @ParameterizedTest
    @MethodSource("invalidCompositions")
    void rejectsInvalidCompositionsWithTheStableCode(String description, List<CompositionComponent> components) {
        assertThatThrownBy(() -> Composition.of(components))
                .as(description)
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCodes.INVALID_COMPOSITION);
                    assertThat(e.getMessage()).startsWith("La composición no es válida");
                });
    }

    static Stream<Arguments> invalidCompositions() {
        return Stream.of(
                Arguments.of("suma 99,99", List.of(CompositionComponent.of("A", "50"), CompositionComponent.of("B", "49.99"))),
                Arguments.of("suma 100,01", List.of(CompositionComponent.of("A", "50"), CompositionComponent.of("B", "50.01"))),
                Arguments.of("suma 90", List.of(CompositionComponent.of("A", "90"))),
                Arguments.of("vacía", List.of()),
                Arguments.of("repetido sin distinguir mayúsculas",
                        List.of(CompositionComponent.of("Algodón", "50"), CompositionComponent.of(" algodón ", "50"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "100.01", "10.123", "150"})
    void rejectsAComponentPercentageOutOfRange(String percentage) {
        assertThatThrownBy(() -> CompositionComponent.of("Algodón", percentage))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.INVALID_COMPOSITION));
    }

    @Test
    void rejectsABlankOrTooLongName() {
        assertThatThrownBy(() -> CompositionComponent.of("  ", "100")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> CompositionComponent.of("x".repeat(81), "100")).isInstanceOf(ValidationException.class);
    }

    // --- promedio ponderado -------------------------------------------------------------------------------------

    @Test
    void oneInputKeepsItsComposition() {
        Composition original = composition("Algodón", "65", "Poliéster", "35");
        Composition result = Composition.weightedAverage(List.of(new Composition.Contribution(original, Quantity.ofKg("123.456"))));
        assertThat(result).isEqualTo(original);
    }

    @Test
    void usReferenceExampleMixesByKg() {
        // 100 kg de 100 % algodón + 300 kg de 50/50 → algodón (100·100 + 300·50)/400 = 62,5 %, poliéster 37,5 %
        Composition result = Composition.weightedAverage(List.of(
                new Composition.Contribution(composition("Algodón", "100"), Quantity.ofKg("100")),
                new Composition.Contribution(composition("Algodón", "50", "Poliéster", "50"), Quantity.ofKg("300"))));
        assertThat(result).isEqualTo(composition("Algodón", "62.5", "Poliéster", "37.5"));
    }

    @Test
    void remainderIsHandedToTheLargestRemainder() {
        // 1/3 de cada una: 33,33 + 33,33 + 33,33 = 99,99 → falta una centésima, que va a la primera (empate)
        Composition result = Composition.weightedAverage(List.of(
                new Composition.Contribution(composition("A", "100"), Quantity.ofKg("1")),
                new Composition.Contribution(composition("B", "100"), Quantity.ofKg("1")),
                new Composition.Contribution(composition("C", "100"), Quantity.ofKg("1"))));
        assertThat(result.components()).extracting(c -> c.percentage().toPlainString())
                .containsExactly("33.34", "33.33", "33.33");
    }

    @Test
    void aComponentThatWouldRoundToZeroIsOmittedAndTheRestStillSums100() {
        // 0,001 kg del componente B frente a 999.999,999 kg de A: B vale < 0,01 %
        Composition result = Composition.weightedAverage(List.of(
                new Composition.Contribution(composition("A", "100"), Quantity.ofKg("999999.999")),
                new Composition.Contribution(composition("B", "100"), Quantity.ofKg("0.001"))));
        assertThat(result).isEqualTo(composition("A", "100"));
    }

    @Test
    void needsAtLeastOneInput() {
        assertThatThrownBy(() -> Composition.weightedAverage(List.of())).isInstanceOf(ValidationException.class);
    }

    /** Propiedad: para cualquier mezcla, el resultado es una composición válida (suma exacta 100,00) y cada componente
     * dista menos de una centésima de su valor exacto Σ kg·% / Σ kg. */
    @ParameterizedTest(name = "caso {index}")
    @MethodSource("randomBlends")
    void weightedAverageIsAValidCompositionWithinOneHundredthOfTheExactValue(List<Composition.Contribution> contributions) {
        Composition result = Composition.weightedAverage(contributions);

        BigInteger totalGrams = contributions.stream().map(c -> c.quantity().grams()).reduce(BigInteger.ZERO, BigInteger::add);
        Map<String, BigDecimal> exact = new HashMap<>();
        for (Composition.Contribution contribution : contributions) {
            for (CompositionComponent component : contribution.composition().components()) {
                BigDecimal weighted = new BigDecimal(contribution.quantity().grams())
                        .multiply(component.percentage())
                        .divide(new BigDecimal(totalGrams), MathContext.DECIMAL128);
                exact.merge(component.key(), weighted, BigDecimal::add);
            }
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (CompositionComponent component : result.components()) {
            assertThat(component.percentage()).isPositive();
            assertThat(exact).containsKey(component.key());
            BigDecimal distance = component.percentage().subtract(exact.get(component.key())).abs();
            assertThat(distance).as("distancia de %s al valor exacto", component.name()).isLessThan(new BigDecimal("0.01"));
            sum = sum.add(component.percentage());
        }
        assertThat(sum).isEqualByComparingTo("100.00");
    }

    /** Propiedad: dos entradas con la misma composición dan esa misma composición, sea cual sea su peso. */
    @ParameterizedTest(name = "caso {index}")
    @MethodSource("randomBlends")
    void inputsWithTheSameCompositionKeepIt(List<Composition.Contribution> contributions) {
        Composition shared = contributions.get(0).composition();
        List<Composition.Contribution> same = new ArrayList<>();
        for (Composition.Contribution contribution : contributions) {
            same.add(new Composition.Contribution(shared, contribution.quantity()));
        }
        assertThat(Composition.weightedAverage(same)).isEqualTo(shared);
    }

    static Stream<List<Composition.Contribution>> randomBlends() {
        Random random = new Random(SEED);
        return IntStream.range(0, CASES).mapToObj(i -> {
            int inputs = 1 + random.nextInt(5);
            List<Composition.Contribution> contributions = new ArrayList<>();
            for (int j = 0; j < inputs; j++) {
                // desde 1 g hasta ~10 t; algunos casos extremos de pocos gramos
                long grams = random.nextInt(4) == 0 ? 1 + random.nextInt(50) : 1 + random.nextInt(10_000_000);
                contributions.add(new Composition.Contribution(randomComposition(random), Quantity.positive(BigDecimal.valueOf(grams, 3))));
            }
            return contributions;
        });
    }

    /** 1 a 4 materiales distintos con porcentajes al azar (centésimas) que suman exactamente 10000. */
    static Composition randomComposition(Random random) {
        int count = 1 + random.nextInt(4);
        List<String> pool = new ArrayList<>(List.of(MATERIALS));
        Collections.shuffle(pool, random);
        // count-1 cortes distintos dentro de (0, 10000) → partes todas positivas
        TreeSet<Integer> cuts = new TreeSet<>();
        while (cuts.size() < count - 1) {
            cuts.add(1 + random.nextInt(Composition.TOTAL_HUNDREDTHS - 1));
        }
        List<CompositionComponent> components = new ArrayList<>();
        int previous = 0;
        int index = 0;
        for (int cut : cuts) {
            components.add(new CompositionComponent(pool.get(index++), BigDecimal.valueOf(cut - previous, 2)));
            previous = cut;
        }
        components.add(new CompositionComponent(pool.get(index), BigDecimal.valueOf(Composition.TOTAL_HUNDREDTHS - previous, 2)));
        return Composition.of(components);
    }

    @Test
    void theGeneratorItselfProducesValidCompositions() {
        Random random = new Random(SEED);
        for (int i = 0; i < 2000; i++) {
            assertThat(randomComposition(random).components()).isNotEmpty();
        }
    }
}

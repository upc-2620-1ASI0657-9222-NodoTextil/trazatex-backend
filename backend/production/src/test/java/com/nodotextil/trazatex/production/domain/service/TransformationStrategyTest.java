package com.nodotextil.trazatex.production.domain.service;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.production.domain.model.BalanceSummary;
import com.nodotextil.trazatex.production.domain.model.Quantity;
import com.nodotextil.trazatex.production.domain.model.TransformationStrategy;
import com.nodotextil.trazatex.production.domain.model.TransformationType;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

import static com.nodotextil.trazatex.production.domain.Fixtures.grams;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Rendimiento mínimo por tipo (decisión del usuario): SPINNING 60 %, WEAVING 80 %, DYEING 80 %. */
class TransformationStrategyTest {

    private static final long SEED = 20261020L;
    private static final int CASES = 1500;

    private static TransformationStrategy strategy(TransformationType type, String minimumPercent) {
        BigDecimal minimum = new BigDecimal(minimumPercent);
        return switch (type) {
            case SPINNING -> new SpinningStrategy(minimum);
            case WEAVING -> new WeavingStrategy(minimum);
            case DYEING -> new DyeingStrategy(minimum);
        };
    }

    private static String defaultMinimum(TransformationType type) {
        return type == TransformationType.SPINNING ? "60" : "80";
    }

    /** Un balance coherente (la merma cierra) con las entradas y salidas dadas en gramos. */
    private static BalanceSummary balance(long inputGrams, long outputGrams) {
        long shrinkage = Math.max(0, inputGrams - outputGrams);
        return new BalanceSummary(grams(inputGrams), grams(outputGrams), Quantity.ZERO,
                Quantity.nonNegative(BigDecimal.valueOf(shrinkage, 3)), 1, 1);
    }

    @ParameterizedTest
    @EnumSource(TransformationType.class)
    void everyStrategyReportsItsType(TransformationType type) {
        assertThat(strategy(type, defaultMinimum(type)).type()).isEqualTo(type);
    }

    @Test
    void spinningRequiresAtLeast60PercentYield() {
        TransformationStrategy spinning = new SpinningStrategy(new BigDecimal("60"));

        assertThatCode(() -> spinning.validate(balance(100_000, 60_000))).doesNotThrowAnyException();
        assertThatCode(() -> spinning.validate(balance(100_000, 100_000))).doesNotThrowAnyException();
        assertThatThrownBy(() -> spinning.validate(balance(100_000, 59_999)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.YIELD_BELOW_MINIMUM));
    }

    @ParameterizedTest
    @ValueSource(strings = {"WEAVING", "DYEING"})
    void weavingAndDyeingRequireAtLeast80PercentYield(String name) {
        TransformationStrategy strategy = strategy(TransformationType.valueOf(name), "80");

        assertThatCode(() -> strategy.validate(balance(100_000, 80_000))).doesNotThrowAnyException();
        assertThatThrownBy(() -> strategy.validate(balance(100_000, 79_999)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.YIELD_BELOW_MINIMUM));
        // el mismo balance que SPINNING acepta (70 %) aquí se rechaza
        assertThatThrownBy(() -> strategy.validate(balance(100_000, 70_000))).isInstanceOf(ValidationException.class);
    }

    @Test
    void theMessageSaysTheObtainedYieldAndTheMinimum() {
        assertThatThrownBy(() -> new SpinningStrategy(new BigDecimal("60")).validate(balance(100_000, 58_333)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getMessage())
                        .contains("58,33 %").contains("mínimo de 60 %").contains("hilandería"));
        assertThatThrownBy(() -> new WeavingStrategy(new BigDecimal("80")).validate(balance(100_000, 50_000)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getMessage())
                        .contains("50 %").contains("mínimo de 80 %").contains("tejeduría"));
        assertThatThrownBy(() -> new DyeingStrategy(new BigDecimal("80")).validate(balance(100_000, 50_000)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getMessage()).contains("tintorería"));
    }

    @Test
    void theDisplayedYieldIsTruncatedSoItNeverLooksLikeItMeetsTheMinimum() {
        // 59,999 % redondearía a 60,00; truncado dice 59,99 y el rechazo no resulta contradictorio
        assertThatThrownBy(() -> new SpinningStrategy(new BigDecimal("60")).validate(balance(100_000, 59_999)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getMessage()).contains("59,99 %").doesNotContain("60,00 %"));
    }

    @Test
    void theMinimumIsConfigurable() {
        TransformationStrategy strict = new SpinningStrategy(new BigDecimal("95.5"));

        assertThatCode(() -> strict.validate(balance(1_000_000, 955_000))).doesNotThrowAnyException();
        assertThatThrownBy(() -> strict.validate(balance(1_000_000, 954_999)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getMessage()).contains("mínimo de 95,5 %"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "100.01", "150"})
    void rejectsAMinimumOutsideZeroToHundred(String minimum) {
        assertThatThrownBy(() -> new SpinningStrategy(new BigDecimal(minimum))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SpinningStrategy(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMinimumOf100PercentIsAllowedAndRequiresNoLoss() {
        TransformationStrategy all = new DyeingStrategy(new BigDecimal("100"));
        assertThatCode(() -> all.validate(balance(5_000, 5_000))).doesNotThrowAnyException();
        assertThatThrownBy(() -> all.validate(balance(5_000, 4_999))).isInstanceOf(ValidationException.class);
    }

    /** Propiedad (≥ 1000 casos por tipo): acepta exactamente cuando salida × 100 ≥ entrada × mínimo, sin redondeos. */
    @ParameterizedTest(name = "{0}")
    @EnumSource(TransformationType.class)
    void acceptsExactlyWhenTheYieldReachesTheMinimum(TransformationType type) {
        String minimum = defaultMinimum(type);
        TransformationStrategy strategy = strategy(type, minimum);
        long minimumPercent = Long.parseLong(minimum);
        Random random = new Random(SEED + type.ordinal());
        int accepted = 0;
        int rejected = 0;
        for (int i = 0; i < CASES; i++) {
            long input = 1 + random.nextInt(5_000_000);
            long boundary = (input * minimumPercent) / 100;
            // la mitad de los casos cae a ±2 g del umbral; el resto, al azar
            long output = i % 2 == 0
                    ? Math.max(1, Math.min(input, boundary + random.nextInt(5) - 2))
                    : 1 + (long) (random.nextDouble() * input);
            boolean expectedOk = output * 100 >= input * minimumPercent;

            if (expectedOk) {
                assertThatCode(() -> strategy.validate(balance(input, output)))
                        .as("%s entrada=%d salida=%d", type, input, output).doesNotThrowAnyException();
                accepted++;
            } else {
                assertThatThrownBy(() -> strategy.validate(balance(input, output)))
                        .as("%s entrada=%d salida=%d", type, input, output)
                        .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.YIELD_BELOW_MINIMUM));
                rejected++;
            }
        }
        assertThat(accepted).isGreaterThan(100);
        assertThat(rejected).isGreaterThan(100);
    }

    // --- fábrica ------------------------------------------------------------------------------------------------

    @Test
    void theFactoryRequiresOneStrategyPerType() {
        List<TransformationStrategy> missing = List.of(new SpinningStrategy(new BigDecimal("60")), new WeavingStrategy(new BigDecimal("80")));
        assertThatThrownBy(() -> new TransformationFactory(missing))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("DYEING");

        List<TransformationStrategy> duplicated = List.of(new SpinningStrategy(new BigDecimal("60")),
                new SpinningStrategy(new BigDecimal("70")), new WeavingStrategy(new BigDecimal("80")), new DyeingStrategy(new BigDecimal("80")));
        assertThatThrownBy(() -> new TransformationFactory(duplicated))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SPINNING");
    }

    @Test
    void theFactoryReturnsTheStrategyOfEachType() {
        TransformationFactory factory = new TransformationFactory(List.of(
                new DyeingStrategy(new BigDecimal("80")), new SpinningStrategy(new BigDecimal("60")), new WeavingStrategy(new BigDecimal("80"))));

        for (TransformationType type : TransformationType.values()) {
            assertThat(factory.strategyFor(type).type()).isEqualTo(type);
        }
    }
}

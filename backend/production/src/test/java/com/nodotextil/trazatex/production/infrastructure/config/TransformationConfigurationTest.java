package com.nodotextil.trazatex.production.infrastructure.config;

import com.nodotextil.trazatex.production.domain.model.BalanceSummary;
import com.nodotextil.trazatex.production.domain.model.Quantity;
import com.nodotextil.trazatex.production.domain.model.TransformationType;
import com.nodotextil.trazatex.production.domain.service.TransformationFactory;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El rendimiento mínimo de cada estrategia sale de {@code application.yml} (60 / 80 / 80) y se puede cambiar con las
 * variables de entorno {@code TRAZATEX_TRANSFORMATION_MIN_YIELD_*}. Carga el {@code application.yml} real: si el binding
 * relajado de Spring no cruzara los guiones de {@code min-yield-percent} (como ocurrió con {@code rate-limit} en el
 * Gateway), estas pruebas lo detectan.
 */
class TransformationConfigurationTest {

    @Configuration
    @EnableConfigurationProperties(TransformationProperties.class)
    static class PropertiesOnly {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesOnly.class);

    private static BalanceSummary balance(long inputGrams, long outputGrams) {
        return new BalanceSummary(Quantity.positive(BigDecimal.valueOf(inputGrams, 3)), Quantity.positive(BigDecimal.valueOf(outputGrams, 3)),
                Quantity.ZERO, Quantity.nonNegative(BigDecimal.valueOf(inputGrams - outputGrams, 3)), 1, 1);
    }

    private TransformationFactory factoryFrom(TransformationProperties properties) {
        return new ProductionConfig().transformationFactory(properties);
    }

    @Test
    void theDefaultsAre60And80And80() {
        runner.run(context -> {
            TransformationProperties.MinYieldPercent min = context.getBean(TransformationProperties.class).minYieldPercent();
            assertThat(min.spinning()).isEqualByComparingTo("60");
            assertThat(min.weaving()).isEqualByComparingTo("80");
            assertThat(min.dyeing()).isEqualByComparingTo("80");
        });
    }

    @Test
    void theEnvironmentVariablesChangeEachMinimumByTheirFullName() {
        runner.withSystemProperties(
                        "TRAZATEX_TRANSFORMATION_MIN_YIELD_SPINNING=55.5",
                        "TRAZATEX_TRANSFORMATION_MIN_YIELD_WEAVING=90",
                        "TRAZATEX_TRANSFORMATION_MIN_YIELD_DYEING=85")
                .run(context -> {
                    TransformationProperties.MinYieldPercent min = context.getBean(TransformationProperties.class).minYieldPercent();
                    assertThat(min.spinning()).isEqualByComparingTo("55.5");
                    assertThat(min.weaving()).isEqualByComparingTo("90");
                    assertThat(min.dyeing()).isEqualByComparingTo("85");
                });
    }

    @Test
    void onlyTheVariableOfThatTypeAffectsThatType() {
        runner.withSystemProperties("TRAZATEX_TRANSFORMATION_MIN_YIELD_WEAVING=95").run(context -> {
            TransformationProperties.MinYieldPercent min = context.getBean(TransformationProperties.class).minYieldPercent();
            assertThat(min.spinning()).isEqualByComparingTo("60");
            assertThat(min.weaving()).isEqualByComparingTo("95");
            assertThat(min.dyeing()).isEqualByComparingTo("80");
        });
    }

    @Test
    void theFactoryBuiltFromTheConfigurationAppliesTheConfiguredMinimums() {
        runner.withSystemProperties("TRAZATEX_TRANSFORMATION_MIN_YIELD_DYEING=90").run(context -> {
            TransformationFactory factory = factoryFrom(context.getBean(TransformationProperties.class));

            factory.strategyFor(TransformationType.SPINNING).validate(balance(100_000, 60_000));
            assertThatThrownBy(() -> factory.strategyFor(TransformationType.SPINNING).validate(balance(100_000, 59_999)))
                    .isInstanceOf(ValidationException.class);
            factory.strategyFor(TransformationType.WEAVING).validate(balance(100_000, 80_000));
            assertThatThrownBy(() -> factory.strategyFor(TransformationType.WEAVING).validate(balance(100_000, 79_999)))
                    .isInstanceOf(ValidationException.class);
            factory.strategyFor(TransformationType.DYEING).validate(balance(100_000, 90_000));
            assertThatThrownBy(() -> factory.strategyFor(TransformationType.DYEING).validate(balance(100_000, 89_999)))
                    .hasMessageContaining("mínimo de 90 %");
        });
    }

    @Test
    void anOutOfRangeMinimumStopsTheStartupNamingTheProperty() {
        for (String invalid : new String[]{"0", "-5", "100.5", "250"}) {
            runner.withSystemProperties("TRAZATEX_TRANSFORMATION_MIN_YIELD_WEAVING=" + invalid).run(context -> {
                TransformationProperties properties = context.getBean(TransformationProperties.class);
                assertThatThrownBy(() -> factoryFrom(properties))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("trazatex.transformation.min-yield-percent.weaving");
            });
        }
    }

    @Test
    void aMissingMinimumStopsTheStartupNamingTheProperty() {
        TransformationProperties missing = new TransformationProperties(
                new TransformationProperties.MinYieldPercent(new BigDecimal("60"), null, new BigDecimal("80")));

        assertThatThrownBy(() -> factoryFrom(missing)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trazatex.transformation.min-yield-percent.weaving");
        assertThatThrownBy(() -> factoryFrom(new TransformationProperties(null))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("min-yield-percent");
    }

    @Test
    void aNonNumericValueFailsToBind() {
        runner.withSystemProperties("TRAZATEX_TRANSFORMATION_MIN_YIELD_SPINNING=sesenta").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootBindException(context.getStartupFailure())).as("un error de binding de la propiedad").isNotNull();
        });
    }

    private static BindException rootBindException(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof BindException bind) {
                return bind;
            }
        }
        return null;
    }
}

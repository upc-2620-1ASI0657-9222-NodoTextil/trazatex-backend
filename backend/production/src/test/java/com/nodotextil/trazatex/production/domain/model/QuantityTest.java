package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuantityTest {

    @ParameterizedTest
    @ValueSource(strings = {"1", "0.001", "12.5", "100.250", "999999999.999", "1.0000000"})
    void acceptsPositiveQuantitiesUpToThreeDecimals(String kg) {
        Quantity quantity = Quantity.positive(new BigDecimal(kg));
        assertThat(quantity.kg().scale()).isEqualTo(Quantity.SCALE);
        assertThat(quantity.kg()).isEqualByComparingTo(kg);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.000", "-1", "-0.001"})
    void positiveRejectsZeroAndNegatives(String kg) {
        assertThatThrownBy(() -> Quantity.positive(new BigDecimal(kg)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.INVALID_QUANTITY));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0001", "1.2345", "10.0001", "0.0005"})
    void rejectsMoreThanThreeDecimalsInsteadOfRoundingSilently(String kg) {
        assertThatThrownBy(() -> Quantity.positive(new BigDecimal(kg)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.INVALID_QUANTITY));
    }

    @Test
    void rejectsAboveTheMaximumAndNull() {
        assertThatThrownBy(() -> Quantity.positive(new BigDecimal("1000000000"))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Quantity.positive(null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Quantity.nonNegative(null)).isInstanceOf(TrazatexException.class);
    }

    @Test
    void nonNegativeAcceptsZero() {
        assertThat(Quantity.nonNegative(BigDecimal.ZERO).isZero()).isTrue();
        assertThat(Quantity.nonNegative(new BigDecimal("0.000"))).isEqualTo(Quantity.ZERO);
    }

    @Test
    void arithmeticIsExact() {
        Quantity a = Quantity.ofKg("0.1");
        Quantity b = Quantity.ofKg("0.2");
        assertThat(a.plus(b)).isEqualTo(Quantity.ofKg("0.3"));
        assertThat(Quantity.ofKg("1").minus(Quantity.ofKg("0.999"))).isEqualByComparingTo("0.001");
        assertThat(Quantity.ofKg("1").minus(Quantity.ofKg("2"))).isEqualByComparingTo("-1");
    }

    @Test
    void equalityIgnoresHowTheValueWasWritten() {
        assertThat(Quantity.ofKg("5")).isEqualTo(Quantity.ofKg("5.000")).hasSameHashCodeAs(Quantity.ofKg("5.0"));
        assertThat(Quantity.ofKg("5")).isNotEqualTo(Quantity.ofKg("5.001"));
    }

    @Test
    void gramsAreAnExactInteger() {
        assertThat(Quantity.ofKg("12.345").grams()).hasToString("12345");
        assertThat(Quantity.ofKg("0.001").grams()).hasToString("1");
    }
}

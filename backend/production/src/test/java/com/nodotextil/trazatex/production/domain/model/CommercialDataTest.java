package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommercialDataTest {

    @Test
    void keepsTheOptionalFields() {
        CommercialData data = new CommercialData(" Tiendas SAC ", new BigDecimal("1250.5"), "pen",
                LocalDate.parse("2026-11-01"), " REF-9 ");
        assertThat(data.buyerOrDistributor()).isEqualTo("Tiendas SAC");
        assertThat(data.salePrice()).isEqualByComparingTo("1250.50");
        assertThat(data.currency()).isEqualTo("PEN");
        assertThat(data.commercialReference()).isEqualTo("REF-9");
    }

    @Test
    void everythingIsOptional() {
        assertThat(new CommercialData(null, null, null, null, null).isEmpty()).isTrue();
        assertThat(new CommercialData("  ", null, "", null, " ").isEmpty()).isTrue();
        assertThat(CommercialData.EMPTY.isEmpty()).isTrue();
    }

    @Test
    void aPriceWithoutCurrencyIsRejected() {
        assertThatThrownBy(() -> new CommercialData(null, new BigDecimal("10"), null, null, null))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.PRICE_REQUIRES_CURRENCY));
        assertThatThrownBy(() -> new CommercialData(null, new BigDecimal("10"), "  ", null, null))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCodes.PRICE_REQUIRES_CURRENCY));
    }

    @Test
    void currencyWithoutPriceIsAllowed() {
        assertThat(new CommercialData(null, null, "USD", null, null).currency()).isEqualTo("USD");
    }

    @Test
    void rejectsAMalformedCurrencyAndANegativeOrTooPreciseAPrice() {
        assertThatThrownBy(() -> new CommercialData(null, null, "SOLES", null, null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new CommercialData(null, new BigDecimal("-1"), "PEN", null, null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new CommercialData(null, new BigDecimal("1.234"), "PEN", null, null)).isInstanceOf(ValidationException.class);
    }
}

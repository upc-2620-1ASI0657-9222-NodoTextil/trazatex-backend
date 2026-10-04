package com.nodotextil.trazatex.production.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BatchOperationalEligibilityTest {

    @Test
    void isEligibleOnlyWhenAvailableNonFinalAndUnblocked() {
        Batch batch = batch(false);

        assertThat(batch.isOperationallyEligible()).isTrue();

        batch.block();
        assertThat(batch.isOperationallyEligible()).isFalse();
        assertThatThrownBy(batch::markAsSplit)
                .isInstanceOf(InvalidBatchException.class)
                .hasMessageContaining("blocked");
        assertThatThrownBy(batch::startTransformation)
                .isInstanceOf(InvalidBatchException.class)
                .hasMessageContaining("blocked");
        assertThatThrownBy(batch::startTransfer)
                .isInstanceOf(InvalidBatchException.class)
                .hasMessageContaining("blocked");

        batch.unblock();
        assertThat(batch.isOperationallyEligible()).isTrue();
    }

    @Test
    void finalProductIsNeverOperationallyEligible() {
        Batch batch = batch(true);

        assertThat(batch.isOperationallyEligible()).isFalse();
        assertThatThrownBy(batch::startTransfer)
                .isInstanceOf(InvalidBatchException.class)
                .hasMessageContaining("final product");
    }

    private static Batch batch(boolean finalProduct) {
        return Batch.register(
                UUID.randomUUID(),
                "TRZ-TEST",
                "QR-TEST",
                UUID.randomUUID(),
                "Supplier",
                "Cusco, Peru",
                MaterialType.FIBER,
                new BigDecimal("10"),
                List.of(new CompositionComponent("Alpaca", new BigDecimal("100"))),
                LocalDateTime.of(2026, 10, 4, 10, 0),
                "Dry",
                finalProduct,
                null,
                null,
                null,
                null,
                null);
    }
}

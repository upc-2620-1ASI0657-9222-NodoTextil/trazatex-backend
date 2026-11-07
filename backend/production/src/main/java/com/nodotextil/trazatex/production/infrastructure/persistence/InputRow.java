package com.nodotextil.trazatex.production.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.util.UUID;

@Embeddable
class InputRow {

    @Column(name = "batch_id", nullable = false)
    UUID batchId;
    @Column(name = "quantity_kg", nullable = false)
    BigDecimal quantityKg;

    protected InputRow() {
    }

    InputRow(UUID batchId, BigDecimal quantityKg) {
        this.batchId = batchId;
        this.quantityKg = quantityKg;
    }
}

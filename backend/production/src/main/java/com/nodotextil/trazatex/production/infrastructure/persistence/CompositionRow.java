package com.nodotextil.trazatex.production.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

@Embeddable
class CompositionRow {

    @Column(name = "component_name", nullable = false)
    String componentName;
    @Column(nullable = false)
    BigDecimal percentage;

    protected CompositionRow() {
    }

    CompositionRow(String componentName, BigDecimal percentage) {
        this.componentName = componentName;
        this.percentage = percentage;
    }
}

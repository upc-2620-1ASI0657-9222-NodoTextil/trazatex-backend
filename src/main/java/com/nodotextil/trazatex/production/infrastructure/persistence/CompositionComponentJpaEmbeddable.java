package com.nodotextil.trazatex.production.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

@Embeddable
class CompositionComponentJpaEmbeddable {

    @Column(name = "material", nullable = false)
    private String material;

    @Column(name = "percentage", nullable = false, precision = 7, scale = 4)
    private BigDecimal percentage;

    protected CompositionComponentJpaEmbeddable() {
    }

    CompositionComponentJpaEmbeddable(String material, BigDecimal percentage) {
        this.material = material;
        this.percentage = percentage;
    }

    String getMaterial() {
        return material;
    }

    BigDecimal getPercentage() {
        return percentage;
    }
}

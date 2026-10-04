package com.nodotextil.trazatex.production.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record CompositionComponent(String material, BigDecimal percentage) {

    public CompositionComponent {
        if (material == null || material.isBlank()) {
            throw new InvalidBatchException("Composition material is required");
        }
        Objects.requireNonNull(percentage, "Composition percentage is required");
        if (percentage.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidBatchException(
                    "Each composition percentage must be greater than zero");
        }
    }
}

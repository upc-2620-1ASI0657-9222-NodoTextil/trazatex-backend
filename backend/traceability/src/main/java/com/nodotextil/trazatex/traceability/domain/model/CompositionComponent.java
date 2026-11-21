package com.nodotextil.trazatex.traceability.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record CompositionComponent(String name, BigDecimal percentage) {

    public CompositionComponent {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El componente de la composición necesita un nombre");
        }
        Objects.requireNonNull(percentage, "percentage");
        percentage = percentage.setScale(2, RoundingMode.HALF_UP);
    }
}

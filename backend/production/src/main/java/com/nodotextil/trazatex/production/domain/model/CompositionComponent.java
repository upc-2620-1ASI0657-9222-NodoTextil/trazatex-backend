package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Un componente de la composición de un lote: material y su porcentaje (> 0 y ≤ 100, hasta 2 decimales). */
public record CompositionComponent(String name, BigDecimal percentage) {

    public static final int SCALE = 2;
    public static final int MAX_NAME_LENGTH = 80;

    public CompositionComponent {
        if (name == null || name.isBlank()) {
            throw invalid("Cada componente de la composición debe tener un nombre.");
        }
        name = name.trim();
        if (name.length() > MAX_NAME_LENGTH) {
            throw invalid("El nombre de un componente admite como máximo " + MAX_NAME_LENGTH + " caracteres.");
        }
        if (percentage == null || percentage.signum() <= 0) {
            throw invalid("El porcentaje de \"" + name + "\" debe ser mayor que cero.");
        }
        if (percentage.stripTrailingZeros().scale() > SCALE) {
            throw invalid("El porcentaje de \"" + name + "\" admite como máximo " + SCALE + " decimales.");
        }
        if (percentage.compareTo(Composition.HUNDRED) > 0) {
            throw invalid("El porcentaje de \"" + name + "\" no puede superar 100 %.");
        }
        percentage = percentage.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static CompositionComponent of(String name, String percentage) {
        return new CompositionComponent(name, new BigDecimal(percentage));
    }

    /** Centésimas de punto porcentual (12,34 % → 1234): la aritmética del promedio ponderado es de enteros. */
    int hundredths() {
        return percentage.movePointRight(SCALE).intValueExact();
    }

    String key() {
        return Composition.keyOf(name);
    }

    private static ValidationException invalid(String message) {
        return new ValidationException(ErrorCodes.INVALID_COMPOSITION, "La composición no es válida: " + message, null);
    }
}

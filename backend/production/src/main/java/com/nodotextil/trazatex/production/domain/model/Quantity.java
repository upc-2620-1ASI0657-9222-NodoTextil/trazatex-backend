package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public final class Quantity implements Comparable<Quantity> {

    public static final int SCALE = 3;
    public static final BigDecimal MAX_KG = new BigDecimal("999999999.999");
    public static final Quantity ZERO = new Quantity(BigDecimal.ZERO.setScale(SCALE));

    private final BigDecimal kg;

    private Quantity(BigDecimal kg) {
        this.kg = kg;
    }

    public static Quantity positive(BigDecimal kg) {
        Quantity quantity = nonNegative(kg);
        if (quantity.kg.signum() == 0) {
            throw invalid("La cantidad debe ser mayor que cero.");
        }
        return quantity;
    }

    public static Quantity nonNegative(BigDecimal kg) {
        if (kg == null) {
            throw invalid("La cantidad es obligatoria.");
        }
        if (kg.signum() < 0) {
            throw invalid("La cantidad no puede ser negativa.");
        }
        if (kg.signum() != 0 && kg.stripTrailingZeros().scale() > SCALE) {
            throw invalid("La cantidad admite como máximo " + SCALE + " decimales (gramos).");
        }
        if (kg.compareTo(MAX_KG) > 0) {
            throw invalid("La cantidad supera el máximo permitido de " + MAX_KG.toPlainString() + " kg.");
        }
        return new Quantity(kg.setScale(SCALE, RoundingMode.UNNECESSARY));
    }

    public static Quantity ofKg(String kg) {
        return positive(new BigDecimal(kg));
    }

    public BigDecimal kg() {
        return kg;
    }

    public BigInteger grams() {
        return kg.movePointRight(SCALE).toBigIntegerExact();
    }

    public Quantity plus(Quantity other) {
        return new Quantity(kg.add(other.kg));
    }

    public BigDecimal minus(Quantity other) {
        return kg.subtract(other.kg);
    }

    public boolean isZero() {
        return kg.signum() == 0;
    }

    @Override
    public int compareTo(Quantity other) {
        return kg.compareTo(other.kg);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Quantity quantity && kg.compareTo(quantity.kg) == 0;
    }

    @Override
    public int hashCode() {
        return kg.hashCode();
    }

    @Override
    public String toString() {
        return kg.toPlainString() + " kg";
    }

    private static ValidationException invalid(String message) {
        return new ValidationException(ErrorCodes.INVALID_QUANTITY, message, null);
    }
}

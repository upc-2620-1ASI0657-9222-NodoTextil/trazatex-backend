package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Cantidad de material en kg (ADR-003). Escala fija de {@value #SCALE} decimales (gramos): un valor con más decimales
 * se rechaza, nunca se redondea en silencio, de modo que sumar y restar es siempre exacto y el balance de masa no
 * acumula error. Nunca es negativa; {@link #positive} exige además que sea mayor que cero.
 */
public final class Quantity implements Comparable<Quantity> {

    public static final int SCALE = 3;
    /** Tope por cantidad (un lote de mil millones de kg ya es un dato erróneo) para no desbordar las columnas numéricas. */
    public static final BigDecimal MAX_KG = new BigDecimal("999999999.999");
    public static final Quantity ZERO = new Quantity(BigDecimal.ZERO.setScale(SCALE));

    private final BigDecimal kg;

    private Quantity(BigDecimal kg) {
        this.kg = kg;
    }

    /** Cantidad mayor que cero: la de un lote, una entrada o una salida. */
    public static Quantity positive(BigDecimal kg) {
        Quantity quantity = nonNegative(kg);
        if (quantity.kg.signum() == 0) {
            throw invalid("La cantidad debe ser mayor que cero.");
        }
        return quantity;
    }

    /** Cantidad mayor o igual que cero: el desperdicio puede ser nulo. */
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

    /** Gramos como entero: la aritmética exacta del reparto por mayor resto trabaja con enteros. */
    public BigInteger grams() {
        return kg.movePointRight(SCALE).toBigIntegerExact();
    }

    public Quantity plus(Quantity other) {
        return new Quantity(kg.add(other.kg));
    }

    /** Diferencia con signo: el balance necesita saber si la merma sería negativa. */
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

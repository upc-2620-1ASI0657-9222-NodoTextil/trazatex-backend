package com.nodotextil.trazatex.production.domain.service;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.production.domain.model.BalanceSummary;
import com.nodotextil.trazatex.production.domain.model.TransformationStrategy;
import com.nodotextil.trazatex.production.domain.model.TransformationType;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Regla común de las estrategias: rendimiento mínimo = {@code salida total / entrada total ≥ mínimo %}. Solo rechaza;
 * no recibe nada que pueda modificar. La comparación es exacta (sin dividir ni redondear); el porcentaje del mensaje se
 * trunca, para que nunca parezca cumplir el mínimo quien no lo cumple.
 */
abstract class MinimumYieldStrategy implements TransformationStrategy {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final TransformationType type;
    private final String processName;
    private final BigDecimal minimumYieldPercent;

    MinimumYieldStrategy(TransformationType type, String processName, BigDecimal minimumYieldPercent) {
        if (minimumYieldPercent == null || minimumYieldPercent.signum() <= 0 || minimumYieldPercent.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("El rendimiento mínimo de " + type + " debe estar entre 0 (exclusivo) y 100 %: "
                    + minimumYieldPercent);
        }
        this.type = type;
        this.processName = processName;
        this.minimumYieldPercent = minimumYieldPercent;
    }

    @Override
    public TransformationType type() {
        return type;
    }

    public BigDecimal minimumYieldPercent() {
        return minimumYieldPercent;
    }

    @Override
    public void validate(BalanceSummary balance) {
        // salida / entrada ≥ mínimo / 100  ⇔  salida × 100 ≥ entrada × mínimo
        BigDecimal output = balance.totalOutput().kg().multiply(HUNDRED);
        BigDecimal required = balance.totalInput().kg().multiply(minimumYieldPercent);
        if (output.compareTo(required) < 0) {
            BigDecimal actual = output.divide(balance.totalInput().kg(), 2, RoundingMode.DOWN);
            throw new ValidationException(ErrorCodes.YIELD_BELOW_MINIMUM,
                    "El rendimiento de la transformación (" + format(actual) + " %) es menor que el mínimo de "
                            + format(minimumYieldPercent) + " % para " + processName + " (salida total / entrada total).",
                    null);
        }
    }

    private static String format(BigDecimal percent) {
        return percent.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}

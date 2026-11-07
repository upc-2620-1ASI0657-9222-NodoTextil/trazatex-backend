package com.nodotextil.trazatex.traceability.application.dto;

import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;
import com.nodotextil.trazatex.traceability.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.traceability.domain.model.CompositionComponent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;

/**
 * Validación de los datos que un evento debe traer. Un evento al que le falta algo obligatorio es ilegible: reintentar no
 * lo arregla, así que el consumidor lo manda a la DLT. Los mensajes nombran el campo y nunca el contenido.
 */
public final class EventFields {

    private EventFields() {
    }

    public static <T> T required(T value, String field) {
        if (value == null || (value instanceof CharSequence text && text.toString().isBlank())) {
            throw invalid(field, "falta");
        }
        return value;
    }

    public static <C extends Collection<?>> C notEmpty(C values, String field) {
        if (values == null || values.isEmpty()) {
            throw invalid(field, "está vacío");
        }
        return values;
    }

    /** Kilogramos positivos, a 3 decimales como en Production. */
    public static BigDecimal positiveKg(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw invalid(field, "debe ser mayor que cero");
        }
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    /** Kilogramos que pueden ser cero (merma, desperdicio). */
    public static BigDecimal nonNegativeKg(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw invalid(field, "no puede ser negativo");
        }
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    public static List<CompositionComponent> compositionOf(List<CompositionComponent> value, String field) {
        return List.copyOf(notEmpty(value, field));
    }

    public static ValidationException invalid(String field, String problem) {
        return new ValidationException(ErrorCodes.INVALID_EVENT, "El evento no es válido: " + field + " " + problem + ".", List.of(field));
    }
}

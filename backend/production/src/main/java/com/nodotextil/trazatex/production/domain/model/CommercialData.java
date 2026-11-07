package com.nodotextil.trazatex.production.domain.model;

import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Datos comerciales opcionales de un producto final (US20, RF-037). Siempre privados: no viajan en ningún evento. Si hay
 * precio, la moneda es obligatoria (código de 3 letras, p. ej. {@code PEN}).
 */
public record CommercialData(
        String buyerOrDistributor,
        BigDecimal salePrice,
        String currency,
        LocalDate commercialExitDate,
        String commercialReference) {

    public static final int PRICE_SCALE = 2;
    public static final CommercialData EMPTY = new CommercialData(null, null, null, null, null);

    public CommercialData {
        buyerOrDistributor = blankToNull(buyerOrDistributor);
        commercialReference = blankToNull(commercialReference);
        currency = blankToNull(currency);
        if (currency != null) {
            currency = currency.toUpperCase(Locale.ROOT);
            if (!currency.matches("[A-Z]{3}")) {
                throw new ValidationException(ErrorCodes.INVALID_COMMERCIAL_DATA,
                        "La moneda debe ser un código de 3 letras (por ejemplo PEN o USD).", null);
            }
        }
        if (salePrice != null) {
            if (salePrice.signum() < 0 || salePrice.stripTrailingZeros().scale() > PRICE_SCALE) {
                throw new ValidationException(ErrorCodes.INVALID_COMMERCIAL_DATA,
                        "El precio de venta debe ser mayor o igual que cero y admite como máximo " + PRICE_SCALE + " decimales.", null);
            }
            salePrice = salePrice.setScale(PRICE_SCALE);
            if (currency == null) {
                throw new ValidationException(ErrorCodes.PRICE_REQUIRES_CURRENCY,
                        "Indique la moneda del precio de venta.", null);
            }
        }
    }

    public boolean isEmpty() {
        return buyerOrDistributor == null && salePrice == null && currency == null
                && commercialExitDate == null && commercialReference == null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

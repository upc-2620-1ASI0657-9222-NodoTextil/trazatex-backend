package com.nodotextil.trazatex.production.application.dto;

import java.math.BigDecimal;
import java.util.List;

/** {@code wasteKg} nulo = sin desperdicio. La merma nunca se ingresa: se calcula. */
public record CompleteTransformationCommand(List<Output> outputs, BigDecimal wasteKg, String wasteReason) {

    /** {@code finalProduct} omitido = {@code false} (es un {@code Boolean} porque Jackson 3 no admite {@code null} en un primitivo). */
    public record Output(String name, String materialType, BigDecimal quantityKg, Boolean finalProduct,
                         CommercialDataInput commercialData) {
    }
}

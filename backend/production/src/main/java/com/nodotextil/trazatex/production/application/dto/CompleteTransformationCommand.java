package com.nodotextil.trazatex.production.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record CompleteTransformationCommand(List<Output> outputs, BigDecimal wasteKg, String wasteReason) {

    public record Output(String name, String materialType, BigDecimal quantityKg, Boolean finalProduct,
                         CommercialDataInput commercialData) {
    }
}

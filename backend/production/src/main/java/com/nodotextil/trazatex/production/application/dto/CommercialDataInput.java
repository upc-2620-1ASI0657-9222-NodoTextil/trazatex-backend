package com.nodotextil.trazatex.production.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CommercialDataInput(
        String buyerOrDistributor,
        BigDecimal salePrice,
        String currency,
        LocalDate commercialExitDate,
        String commercialReference) {
}

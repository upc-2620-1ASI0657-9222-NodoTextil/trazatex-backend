package com.nodotextil.trazatex.production.infrastructure.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MarkFinalProductRequest(
        String buyerOrDistributor,
        BigDecimal price,
        String currency,
        LocalDate commercialDate,
        String commercialReference) {
}

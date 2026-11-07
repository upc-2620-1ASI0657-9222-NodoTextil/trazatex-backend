package com.nodotextil.trazatex.production.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record RegisterBatchCommand(
        String name,
        String materialType,
        List<CompositionComponentInput> composition,
        BigDecimal quantityKg,
        String origin,
        String supplierName,
        String receptionCharacteristics) {
}

package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.domain.MaterialType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RegisterBatchRequest(
        @NotNull UUID responsibleCompanyId,
        String supplierName,
        @NotBlank String geographicOrigin,
        @NotNull MaterialType materialType,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantityKg,
        @NotEmpty List<@Valid CompositionComponentRequest> composition,
        @NotBlank String receptionCharacteristics) {

    public record CompositionComponentRequest(
            @NotBlank String material,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal percentage) {
    }
}

package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.domain.MaterialType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record CompleteTransformationRequest(
        @NotEmpty List<@Valid OutputRequest> outputs) {

    public record OutputRequest(
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantityKg,
            @NotNull MaterialType materialType,
            @NotBlank String geographicOrigin,
            @NotBlank String receptionCharacteristics) {
    }
}

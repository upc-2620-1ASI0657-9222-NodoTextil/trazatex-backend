package com.nodotextil.trazatex.production.infrastructure.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record SplitBatchRequest(
        @NotNull
        @Size(min = 2)
        List<@NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal> quantitiesKg) {
}

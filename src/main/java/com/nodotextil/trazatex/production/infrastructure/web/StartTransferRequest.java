package com.nodotextil.trazatex.production.infrastructure.web;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record StartTransferRequest(
        @NotNull UUID destinationCompanyId,
        @NotEmpty List<@NotNull UUID> batchIds) {
}

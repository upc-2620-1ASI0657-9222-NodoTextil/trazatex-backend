package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.domain.TransformationType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record StartTransformationRequest(
        @NotNull UUID machineId,
        @NotNull TransformationType type,
        @NotEmpty List<@NotNull UUID> inputBatchIds) {
}

package com.nodotextil.trazatex.production.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RegisterMachineRequest(
        @NotBlank String internalCode,
        @NotBlank String name,
        @NotBlank String type,
        @NotNull UUID companyId) {
}

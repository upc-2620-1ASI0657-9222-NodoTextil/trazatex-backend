package com.nodotextil.trazatex.production.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

public record RegisterMachineRequest(
        @NotBlank String internalCode,
        @NotBlank String name,
        @NotBlank String type) {
}

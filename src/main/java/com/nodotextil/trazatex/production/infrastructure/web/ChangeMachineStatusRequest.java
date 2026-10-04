package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.domain.MachineStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeMachineStatusRequest(@NotNull MachineStatus status) {
}

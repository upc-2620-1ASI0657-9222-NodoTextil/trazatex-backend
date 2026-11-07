package com.nodotextil.trazatex.production.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Una fila del historial de estados de una maquinaria ({@code machine_status_history}). */
public record MachineStatusChange(UUID machineId, MachineStatus from, MachineStatus to, UUID changedBy, Instant changedAt) {
}

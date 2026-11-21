package com.nodotextil.trazatex.production.domain.model;

import java.time.Instant;
import java.util.UUID;

public record MachineStatusChange(UUID machineId, MachineStatus from, MachineStatus to, UUID changedBy, Instant changedAt) {
}

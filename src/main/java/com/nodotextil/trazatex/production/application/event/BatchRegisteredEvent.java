package com.nodotextil.trazatex.production.application.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record BatchRegisteredEvent(UUID batchId, LocalDateTime occurredAt) {
}

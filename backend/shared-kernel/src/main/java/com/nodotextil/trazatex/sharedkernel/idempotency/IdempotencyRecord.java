package com.nodotextil.trazatex.sharedkernel.idempotency;

import java.time.Instant;

public record IdempotencyRecord(
        String key,
        String userId,
        String requestHash,
        int responseStatus,
        String responseBody,
        Instant createdAt) {
}

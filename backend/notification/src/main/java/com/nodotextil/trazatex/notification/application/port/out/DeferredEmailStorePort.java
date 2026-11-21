package com.nodotextil.trazatex.notification.application.port.out;

import com.nodotextil.trazatex.notification.application.dto.DueEmail;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DeferredEmailStorePort {
    int expireDue();

    List<DueEmail> claimDue(int limit, Duration lease);

    void markSent(UUID id);
    void markUndeliverable(UUID id, String reason);
    void markRetry(UUID id, Instant nextAttempt, String reason);
    void markFailed(UUID id, String reason);
    void release(UUID id, Instant nextAttempt);
}

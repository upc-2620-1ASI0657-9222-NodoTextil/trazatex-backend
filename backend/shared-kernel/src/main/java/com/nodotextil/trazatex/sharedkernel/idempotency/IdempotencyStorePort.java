package com.nodotextil.trazatex.sharedkernel.idempotency;

import java.time.Instant;
import java.util.Optional;

public interface IdempotencyStorePort {

    boolean tryReserve(String key, String userId, String requestHash, Instant createdAt);

    Optional<IdempotencyRecord> find(String key);

    void complete(String key, int responseStatus, String responseBody);

    void delete(String key);

    int deleteCreatedBefore(Instant cutoff);
}

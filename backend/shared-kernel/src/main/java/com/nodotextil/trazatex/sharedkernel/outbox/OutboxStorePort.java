package com.nodotextil.trazatex.sharedkernel.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxStorePort {

    void save(OutboxMessage message);

    List<OutboxMessage> findPending(int limit);

    void markPublished(UUID id, Instant publishedAt);

    void markFailed(UUID id, String error);
}

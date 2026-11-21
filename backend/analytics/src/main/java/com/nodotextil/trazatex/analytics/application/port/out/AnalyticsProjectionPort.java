package com.nodotextil.trazatex.analytics.application.port.out;

import java.time.Instant;
import java.util.UUID;

public interface AnalyticsProjectionPort {

    record RecordedEvent(UUID eventId, String type, String aggregateId, Instant occurredAt, UUID companyId, Instant businessAt, String payloadJson) { }

    boolean record(RecordedEvent event);

    void birth(UUID eventId, UUID batchId, UUID companyId, Instant at, String qualityStatus);

    void owner(UUID eventId, UUID batchId, UUID companyId, Instant at, long batchVersion);

    void quality(UUID eventId, UUID batchId, String status, Instant at);
}

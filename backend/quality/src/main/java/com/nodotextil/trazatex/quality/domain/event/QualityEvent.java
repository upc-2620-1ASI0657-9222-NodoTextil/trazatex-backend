package com.nodotextil.trazatex.quality.domain.event;

public interface QualityEvent {

    String eventType();

    String aggregateId();
}

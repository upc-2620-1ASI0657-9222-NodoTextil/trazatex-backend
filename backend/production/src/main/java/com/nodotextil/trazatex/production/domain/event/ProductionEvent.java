package com.nodotextil.trazatex.production.domain.event;

public interface ProductionEvent {

    String eventType();

    String aggregateId();
}

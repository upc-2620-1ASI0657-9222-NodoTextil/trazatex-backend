package com.nodotextil.trazatex.production.application;

import com.nodotextil.trazatex.production.application.event.BatchEventPublisher;
import com.nodotextil.trazatex.production.application.event.BatchSplitEvent;
import java.util.ArrayList;
import java.util.List;

final class TestBatchEventPublisher implements BatchEventPublisher {

    private final List<BatchSplitEvent> splitEvents = new ArrayList<>();

    @Override
    public void publish(BatchSplitEvent event) {
        splitEvents.add(event);
    }

    List<BatchSplitEvent> splitEvents() {
        return List.copyOf(splitEvents);
    }
}

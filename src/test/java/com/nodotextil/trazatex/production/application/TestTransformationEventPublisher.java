package com.nodotextil.trazatex.production.application;

import com.nodotextil.trazatex.production.application.event.TransformationCompletedEvent;
import com.nodotextil.trazatex.production.application.event.TransformationEventPublisher;
import java.util.ArrayList;
import java.util.List;

final class TestTransformationEventPublisher implements TransformationEventPublisher {

    private final List<TransformationCompletedEvent> completedEvents = new ArrayList<>();

    @Override
    public void publish(TransformationCompletedEvent event) {
        completedEvents.add(event);
    }

    List<TransformationCompletedEvent> completedEvents() {
        return List.copyOf(completedEvents);
    }
}

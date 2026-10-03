package com.nodotextil.trazatex.quality.infrastructure.event;

import com.nodotextil.trazatex.quality.application.contract.QualityEventPublisher;
import com.nodotextil.trazatex.quality.domain.event.FailureConfirmedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SpringQualityEventPublisher implements QualityEventPublisher {

    private final ApplicationEventPublisher publisher;

    public SpringQualityEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(FailureConfirmedEvent event) {
        publisher.publishEvent(event);
    }
}

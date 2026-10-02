package com.nodotextil.trazatex.quality.application.contract;

import com.nodotextil.trazatex.quality.domain.event.FailureConfirmedEvent;

public interface QualityEventPublisher {

    void publish(FailureConfirmedEvent event);
}

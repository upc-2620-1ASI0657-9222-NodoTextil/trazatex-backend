package com.nodotextil.trazatex.quality.application.port.out;

import com.nodotextil.trazatex.quality.domain.event.QualityEvent;

public interface QualityEventPublisherPort {

    void publish(QualityEvent event);
}

package com.nodotextil.trazatex.production.application.port.out;

import com.nodotextil.trazatex.production.domain.event.ProductionEvent;

public interface ProductionEventPublisherPort {

    void publish(ProductionEvent event);
}

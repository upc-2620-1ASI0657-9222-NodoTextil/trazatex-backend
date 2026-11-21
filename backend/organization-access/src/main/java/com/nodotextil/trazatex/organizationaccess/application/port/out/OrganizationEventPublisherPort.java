package com.nodotextil.trazatex.organizationaccess.application.port.out;

import com.nodotextil.trazatex.organizationaccess.domain.event.OrganizationEvent;

public interface OrganizationEventPublisherPort {

    void publish(OrganizationEvent event);
}

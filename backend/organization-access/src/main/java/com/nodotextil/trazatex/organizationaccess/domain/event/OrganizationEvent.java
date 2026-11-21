package com.nodotextil.trazatex.organizationaccess.domain.event;

public interface OrganizationEvent {

    String type();

    String aggregateId();
}

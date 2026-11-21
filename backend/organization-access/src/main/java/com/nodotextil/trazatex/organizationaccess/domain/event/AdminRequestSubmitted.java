package com.nodotextil.trazatex.organizationaccess.domain.event;

import java.util.UUID;

public record AdminRequestSubmitted(UUID requestId, UUID companyId) implements OrganizationEvent {
    @Override public String type() { return "AdminRequestSubmitted"; }
    @Override public String aggregateId() { return requestId.toString(); }
}

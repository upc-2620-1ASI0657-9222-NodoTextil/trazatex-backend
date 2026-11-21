package com.nodotextil.trazatex.organizationaccess.application.dto;

import java.util.UUID;

public record CreateCompanyResult(CompanyResult company, UUID adminInvitationId) {
}

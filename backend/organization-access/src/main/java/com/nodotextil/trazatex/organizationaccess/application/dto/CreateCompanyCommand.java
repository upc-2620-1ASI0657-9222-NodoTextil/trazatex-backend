package com.nodotextil.trazatex.organizationaccess.application.dto;

import com.nodotextil.trazatex.organizationaccess.domain.model.TextileActivity;

import java.util.Set;

public record CreateCompanyCommand(String ruc, String businessName, Set<TextileActivity> activities, String adminEmail) {
}

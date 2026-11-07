package com.nodotextil.trazatex.organizationaccess.application.dto;

import com.nodotextil.trazatex.organizationaccess.domain.model.CompanyPrivacy;
import com.nodotextil.trazatex.organizationaccess.domain.model.ConfigurableField;
import com.nodotextil.trazatex.organizationaccess.domain.model.Visibility;

import java.util.Map;
import java.util.UUID;

public record PrivacyResult(UUID companyId, Map<ConfigurableField, Visibility> fields) {

    public static PrivacyResult from(CompanyPrivacy privacy) {
        return new PrivacyResult(privacy.companyId(), privacy.asMap());
    }
}

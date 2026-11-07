package com.nodotextil.trazatex.organizationaccess.application.port.out;

import com.nodotextil.trazatex.organizationaccess.domain.model.CompanyPrivacy;

import java.util.UUID;

public interface PrivacyRepositoryPort {

    /** Siempre completa: lo que falte se toma PRIVATE. */
    CompanyPrivacy find(UUID companyId);

    void save(CompanyPrivacy privacy);
}

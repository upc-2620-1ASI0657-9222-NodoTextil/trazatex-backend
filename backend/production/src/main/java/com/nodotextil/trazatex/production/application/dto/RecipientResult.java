package com.nodotextil.trazatex.production.application.dto;

import com.nodotextil.trazatex.production.domain.model.CompanyProjection;

import java.util.UUID;

/** Una empresa a la que se puede transferir: solo su id y su razón social. */
public record RecipientResult(UUID id, String businessName) {

    public static RecipientResult from(CompanyProjection company) {
        return new RecipientResult(company.companyId(), company.businessName());
    }
}

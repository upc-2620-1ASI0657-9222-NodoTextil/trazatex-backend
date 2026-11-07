package com.nodotextil.trazatex.organizationaccess.domain.event;

import com.nodotextil.trazatex.organizationaccess.domain.model.CompanyPrivacy;
import com.nodotextil.trazatex.organizationaccess.domain.model.ConfigurableField;
import com.nodotextil.trazatex.organizationaccess.domain.model.Visibility;

import java.util.Map;
import java.util.UUID;

/** Tópico {@code orgaccess.company}, clave companyId. Lo consume traceability. {@code fields} es siempre el mapa completo. */
public record CompanyPrivacyUpdated(UUID companyId, Map<ConfigurableField, Visibility> fields) implements OrganizationEvent {

    public static final String TYPE = "CompanyPrivacyUpdated";

    public CompanyPrivacyUpdated {
        fields = Map.copyOf(fields);
    }

    public static CompanyPrivacyUpdated of(CompanyPrivacy privacy) {
        return new CompanyPrivacyUpdated(privacy.companyId(), privacy.asMap());
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String aggregateId() {
        return companyId.toString();
    }
}

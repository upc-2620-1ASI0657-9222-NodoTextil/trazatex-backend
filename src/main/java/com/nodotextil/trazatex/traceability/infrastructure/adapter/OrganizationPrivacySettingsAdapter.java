package com.nodotextil.trazatex.traceability.infrastructure.adapter;

import com.nodotextil.trazatex.organizationaccess.application.contract.CompanyPrivacyAccess;
import com.nodotextil.trazatex.organizationaccess.application.contract.PrivacyField;
import com.nodotextil.trazatex.traceability.application.port.PrivacySettingsPort;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class OrganizationPrivacySettingsAdapter implements PrivacySettingsPort {

    private static final Map<PrivacyField, String> CONFIGURABLE_KEYS = Map.of(
            PrivacyField.SUPPLIER, "supplierName",
            PrivacyField.GEOGRAPHIC_ORIGIN, "geographicOrigin",
            PrivacyField.QUANTITY, "quantityKg",
            PrivacyField.COMPOSITION, "composition",
            PrivacyField.RECEPTION_CHARACTERISTICS, "receptionCharacteristics");

    private final CompanyPrivacyAccess companyPrivacyAccess;

    OrganizationPrivacySettingsAdapter(CompanyPrivacyAccess companyPrivacyAccess) {
        this.companyPrivacyAccess = companyPrivacyAccess;
    }

    @Override
    public Set<String> sharedConfigurableFields(UUID companyId) {
        Set<PrivacyField> shared = companyPrivacyAccess.sharedFields(companyId);
        return CONFIGURABLE_KEYS.entrySet().stream()
                .filter(entry -> shared.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .collect(Collectors.toSet());
    }
}
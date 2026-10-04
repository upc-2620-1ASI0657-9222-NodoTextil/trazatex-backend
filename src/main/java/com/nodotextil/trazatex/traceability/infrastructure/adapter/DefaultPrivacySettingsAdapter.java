package com.nodotextil.trazatex.traceability.infrastructure.adapter;

import com.nodotextil.trazatex.traceability.application.port.PrivacySettingsPort;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
class DefaultPrivacySettingsAdapter implements PrivacySettingsPort {

    @Override
    public Set<String> sharedConfigurableFields(UUID companyId) {
        return Set.of();
    }
}

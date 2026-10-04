package com.nodotextil.trazatex.traceability.application.port;

import java.util.Set;
import java.util.UUID;

public interface PrivacySettingsPort {

    Set<String> sharedConfigurableFields(UUID companyId);
}

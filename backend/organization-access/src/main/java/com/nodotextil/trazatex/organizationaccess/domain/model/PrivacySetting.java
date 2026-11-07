package com.nodotextil.trazatex.organizationaccess.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Visibilidad B2B de un campo configurable de una empresa (US11). */
public record PrivacySetting(UUID companyId, ConfigurableField field, Visibility visibility) {

    public PrivacySetting {
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(visibility, "visibility");
    }

    /** Valor inicial de todo campo configurable (US11 esc. 1). */
    public static PrivacySetting privateByDefault(UUID companyId, ConfigurableField field) {
        return new PrivacySetting(companyId, field, Visibility.PRIVATE);
    }

    public PrivacySetting withVisibility(Visibility newVisibility) {
        return new PrivacySetting(companyId, field, newVisibility);
    }
}

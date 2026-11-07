package com.nodotextil.trazatex.traceability.domain.model;

/**
 * Campos que cada empresa decide compartir o no con las demás (US11, {@code docs/domain/organization-access.md}); son las
 * claves del mapa de {@code CompanyPrivacyUpdated}. Los demás campos son siempre compartidos o siempre privados y no
 * se configuran.
 */
public enum ConfigurableField {
    SUPPLIER,
    ORIGIN,
    QUANTITY,
    COMPOSITION,
    RECEPTION_CHARACTERISTICS,
    MACHINERY,
    SHRINKAGE,
    WASTE,
    WASTE_REASON,
    FAILURE_CAUSE,
    QUALITY_TEST_VALUES
}

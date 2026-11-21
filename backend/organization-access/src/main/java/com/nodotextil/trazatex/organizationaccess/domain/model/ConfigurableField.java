package com.nodotextil.trazatex.organizationaccess.domain.model;

import java.util.Arrays;
import java.util.Optional;

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
    QUALITY_TEST_VALUES;

    public static Optional<ConfigurableField> fromName(String name) {
        return Arrays.stream(values()).filter(field -> field.name().equals(name)).findFirst();
    }
}

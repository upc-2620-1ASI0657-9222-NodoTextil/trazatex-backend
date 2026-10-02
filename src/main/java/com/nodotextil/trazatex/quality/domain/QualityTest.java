package com.nodotextil.trazatex.quality.domain;

import java.util.Objects;
import java.util.UUID;

public record QualityTest(
        UUID id,
        String criterion,
        String expectedValue,
        String actualValue,
        String unit,
        TestResult result,
        String observations) {

    public QualityTest {
        Objects.requireNonNull(id, "Test id is required");
        criterion = requireText(criterion, "Test criterion is required");
        expectedValue = requireText(expectedValue, "Expected value is required");
        actualValue = requireText(actualValue, "Actual value is required");
        unit = requireText(unit, "Test unit is required");
        Objects.requireNonNull(result, "Test result is required");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidQualityControlException(message);
        }

        return value;
    }
}

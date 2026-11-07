package com.nodotextil.trazatex.quality.domain.model;

import com.nodotextil.trazatex.quality.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.sharedkernel.exception.ValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Una prueba de un control (US24): criterio, valor esperado, valor obtenido, unidad, resultado y observaciones. Todo es obligatorio
 * salvo las observaciones. Los valores son texto: un criterio puede ser cuantitativo ("95", "%") o cualitativo.
 */
public record QualityTest(UUID id, UUID controlId, String criterionName, String expectedValue, String obtainedValue, String unit,
                          TestResult testResult, String remarks) {

    public QualityTest {
        List<String> missing = new ArrayList<>();
        if (id == null) {
            missing.add("id");
        }
        if (controlId == null) {
            missing.add("controlId");
        }
        criterionName = clean(criterionName);
        expectedValue = clean(expectedValue);
        obtainedValue = clean(obtainedValue);
        unit = clean(unit);
        remarks = clean(remarks);
        if (criterionName == null) {
            missing.add("criterionName");
        }
        if (expectedValue == null) {
            missing.add("expectedValue");
        }
        if (obtainedValue == null) {
            missing.add("obtainedValue");
        }
        if (unit == null) {
            missing.add("unit");
        }
        if (testResult == null) {
            missing.add("testResult");
        }
        if (!missing.isEmpty()) {
            throw new ValidationException(ErrorCodes.MISSING_FIELDS, "Faltan datos de la prueba: " + String.join(", ", missing), missing);
        }
    }

    public boolean failed() {
        return testResult == TestResult.FAILED;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

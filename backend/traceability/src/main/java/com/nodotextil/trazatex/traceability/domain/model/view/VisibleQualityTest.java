package com.nodotextil.trazatex.traceability.domain.model.view;

public record VisibleQualityTest(String criterionName, String expectedValue, String obtainedValue, String unit, String testResult) implements VisibleView {
}

package com.nodotextil.trazatex.traceability.domain.model.view;

/** El valor de una prueba de calidad tal como lo ve quien consulta (solo la dueña o, si se compartió {@code QUALITY_TEST_VALUES}, otra empresa). */
public record VisibleQualityTest(String criterionName, String expectedValue, String obtainedValue, String unit, String testResult) implements VisibleView {
}

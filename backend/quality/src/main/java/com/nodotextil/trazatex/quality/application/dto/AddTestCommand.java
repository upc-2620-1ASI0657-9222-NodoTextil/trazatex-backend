package com.nodotextil.trazatex.quality.application.dto;

import com.nodotextil.trazatex.quality.domain.model.TestResult;

/** US24: criterio, valor esperado, valor obtenido, unidad, resultado y observaciones (opcionales). */
public record AddTestCommand(String criterionName, String expectedValue, String obtainedValue, String unit, TestResult testResult, String remarks) {
}

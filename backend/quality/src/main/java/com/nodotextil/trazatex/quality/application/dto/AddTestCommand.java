package com.nodotextil.trazatex.quality.application.dto;

import com.nodotextil.trazatex.quality.domain.model.TestResult;

public record AddTestCommand(String criterionName, String expectedValue, String obtainedValue, String unit, TestResult testResult, String remarks) {
}

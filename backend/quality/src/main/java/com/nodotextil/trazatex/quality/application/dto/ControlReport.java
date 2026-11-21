package com.nodotextil.trazatex.quality.application.dto;

import com.nodotextil.trazatex.quality.domain.model.ControlType;
import com.nodotextil.trazatex.quality.domain.model.TestResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ControlReport(UUID controlId, String batchReference, ControlType controlType, Instant startedAt, Instant finishedAt, TestResult result,
                            List<Test> tests, Instant generatedAt) {

    public record Test(String criterion, String expectedValue, String obtainedValue, String unit, TestResult result, String remarks) {
    }
}

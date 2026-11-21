package com.nodotextil.trazatex.traceability.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.nodotextil.trazatex.traceability.application.dto.EventFields.required;

public record ApplyQualityTestsCommand(UUID eventId, UUID batchId, Instant occurredAt, List<TestValue> tests) {

    public record TestValue(String criterionName, String expectedValue, String obtainedValue, String unit, String testResult) {
    }

    public ApplyQualityTestsCommand {
        required(eventId, "eventId");
        required(batchId, "batchId");
        required(occurredAt, "occurredAt");
        tests = List.copyOf(required(tests, "tests"));
    }
}

package com.nodotextil.trazatex.quality.application.contract;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QualityReportQueries {

    Optional<CompletedControlSnapshot> findCompletedControl(UUID controlId);

    record CompletedControlSnapshot(
            UUID controlId,
            UUID batchId,
            List<TestSnapshot> tests) {

        public CompletedControlSnapshot {
            tests = List.copyOf(tests);
        }
    }

    record TestSnapshot(
            String criterion,
            String expectedValue,
            String actualValue,
            String unit,
            String result,
            String observations) {
    }
}

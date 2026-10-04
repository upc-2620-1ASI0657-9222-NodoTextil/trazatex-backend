package com.nodotextil.trazatex.analytics.domain;

public record ReportTest(
        String criterion,
        String expectedValue,
        String actualValue,
        String unit,
        ReportTestResult result) {}
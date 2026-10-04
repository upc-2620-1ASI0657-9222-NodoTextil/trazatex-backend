package com.nodotextil.trazatex.analytics.domain;

import com.nodotextil.trazatex.analytics.domain.ReportTestResult;
import java.util.List;
import java.util.UUID;

public interface PdfGeneratorPort {
    GeneratedPdf generateQualityControlReport(UUID controlId, List<ReportTest> tests);
    record GeneratedPdf(String url) {}
}
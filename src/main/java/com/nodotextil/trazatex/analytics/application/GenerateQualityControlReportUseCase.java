package com.nodotextil.trazatex.analytics.application;

import com.nodotextil.trazatex.analytics.domain.PdfGeneratorPort;
import com.nodotextil.trazatex.analytics.domain.ReportTest;
import com.nodotextil.trazatex.analytics.domain.ReportTestResult;
import com.nodotextil.trazatex.quality.application.contract.QualityReportQueries;
import java.util.UUID;

public class GenerateQualityControlReportUseCase {

    private final QualityReportQueries reportQueries;
    private final PdfGeneratorPort pdfGenerator;

    public GenerateQualityControlReportUseCase(
            QualityReportQueries reportQueries,
            PdfGeneratorPort pdfGenerator) {
        this.reportQueries = reportQueries;
        this.pdfGenerator = pdfGenerator;
    }

    public PdfGeneratorPort.GeneratedPdf execute(UUID controlId) {
        var snapshot = reportQueries.findCompletedControl(controlId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Quality control not found or not yet completed: " + controlId));

        var tests = snapshot.tests().stream()
                .map(t -> new ReportTest(
                        t.criterion(),
                        t.expectedValue(),
                        t.actualValue(),
                        t.unit(),
                        ReportTestResult.valueOf(t.result())))
                .toList();

        return pdfGenerator.generateQualityControlReport(snapshot.controlId(), tests);
    }
}
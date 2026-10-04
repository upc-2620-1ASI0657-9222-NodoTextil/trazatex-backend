package com.nodotextil.trazatex.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.nodotextil.trazatex.analytics.domain.PdfGeneratorPort;
import com.nodotextil.trazatex.analytics.domain.ReportTest;
import com.nodotextil.trazatex.analytics.domain.ReportTestResult;
import com.nodotextil.trazatex.quality.application.contract.QualityReportQueries;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GenerateQualityControlReportUseCaseTest {

    private final QualityReportQueries reportQueries = mock(QualityReportQueries.class);
    private final PdfGeneratorPort pdfGenerator = mock(PdfGeneratorPort.class);
    private final GenerateQualityControlReportUseCase useCase =
            new GenerateQualityControlReportUseCase(reportQueries, pdfGenerator);

    @Test
    void generatesReportForCompletedControl() {
        UUID controlId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        var snapshot = new QualityReportQueries.CompletedControlSnapshot(
                controlId, batchId,
                List.of(new QualityReportQueries.TestSnapshot(
                        "color", "azul", "azul", "visual", "PASSED")));

        when(reportQueries.findCompletedControl(controlId)).thenReturn(Optional.of(snapshot));
        when(pdfGenerator.generateQualityControlReport(eq(controlId), anyList()))
                .thenReturn(new PdfGeneratorPort.GeneratedPdf("https://pdf.example.com/report.pdf"));

        var result = useCase.execute(controlId);

        assertThat(result.url()).isEqualTo("https://pdf.example.com/report.pdf");
        verify(pdfGenerator).generateQualityControlReport(
                eq(controlId),
                argThat(tests -> tests.size() == 1
                        && tests.get(0).criterion().equals("color")
                        && tests.get(0).result() == ReportTestResult.PASSED));
    }

    @Test
    void throwsWhenControlNotFoundOrNotCompleted() {
        UUID controlId = UUID.randomUUID();
        when(reportQueries.findCompletedControl(controlId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(controlId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(controlId.toString());
    }
}
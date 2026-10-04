package com.nodotextil.trazatex.analytics.infrastructure;

import com.nodotextil.trazatex.analytics.domain.PdfGeneratorPort;
import com.nodotextil.trazatex.analytics.domain.ReportTest;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.external-services.enabled", havingValue = "false", matchIfMissing = true)
public class DevPdfGeneratorAdapter implements PdfGeneratorPort {

    @Override
    public GeneratedPdf generateQualityControlReport(UUID controlId, List<ReportTest> tests) {
        return new GeneratedPdf("dev://analytics/quality-controls/" + controlId + "/report.pdf");
    }
}
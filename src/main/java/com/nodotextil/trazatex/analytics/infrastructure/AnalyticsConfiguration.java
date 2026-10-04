package com.nodotextil.trazatex.analytics.infrastructure;

import com.nodotextil.trazatex.analytics.application.GenerateQualityControlReportUseCase;
import com.nodotextil.trazatex.analytics.domain.PdfGeneratorPort;
import com.nodotextil.trazatex.quality.application.contract.QualityReportQueries;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnalyticsConfiguration {

    @Bean
    GenerateQualityControlReportUseCase generateQualityControlReportUseCase(
            QualityReportQueries reportQueries,
            PdfGeneratorPort pdfGenerator) {
        return new GenerateQualityControlReportUseCase(reportQueries, pdfGenerator);
    }
}
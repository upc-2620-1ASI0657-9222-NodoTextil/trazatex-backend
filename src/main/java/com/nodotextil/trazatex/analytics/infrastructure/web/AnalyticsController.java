package com.nodotextil.trazatex.analytics.infrastructure.web;

import com.nodotextil.trazatex.analytics.application.AnalyticsSummary;
import com.nodotextil.trazatex.analytics.application.AnalyticsSummaryService;
import com.nodotextil.trazatex.analytics.application.GenerateQualityControlReportUseCase;
import com.nodotextil.trazatex.analytics.domain.PdfGeneratorPort;
import com.nodotextil.trazatex.quality.application.QualityAccessService;
import com.nodotextil.trazatex.shared.security.AuthenticatedUser;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@PreAuthorize("hasRole('COMPANY_ADMIN')")
public class AnalyticsController {

    private final AnalyticsSummaryService summaries;
    private final GenerateQualityControlReportUseCase reports;
    private final QualityAccessService qualityAccess;

    public AnalyticsController(
            AnalyticsSummaryService summaries,
            GenerateQualityControlReportUseCase reports,
            QualityAccessService qualityAccess) {
        this.summaries = summaries;
        this.reports = reports;
        this.qualityAccess = qualityAccess;
    }

    @GetMapping("/summary")
    public AnalyticsSummary summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Jwt jwt) {
        UUID companyId = AuthenticatedUser.from(jwt).requireCompanyId();
        return summaries.summarize(companyId, from, to);
    }

    @PostMapping("/quality-controls/{controlId}/report")
    public PdfGeneratorPort.GeneratedPdf generateReport(
            @PathVariable UUID controlId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID companyId = AuthenticatedUser.from(jwt).requireCompanyId();
        qualityAccess.requireControlCompany(controlId, companyId);
        return reports.execute(controlId);
    }
}

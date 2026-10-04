package com.nodotextil.trazatex.analytics.infrastructure.web;

import com.nodotextil.trazatex.analytics.application.AnalyticsSummaryService;
import com.nodotextil.trazatex.analytics.application.AnalyticsSummary;
import com.nodotextil.trazatex.analytics.application.GenerateQualityControlReportUseCase;
import com.nodotextil.trazatex.analytics.domain.PdfGeneratorPort;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsSummaryService summaries;
    private final GenerateQualityControlReportUseCase reports;

    public AnalyticsController(
            AnalyticsSummaryService summaries,
            GenerateQualityControlReportUseCase reports) {
        this.summaries = summaries;
        this.reports = reports;
    }

    @GetMapping("/summary")
    public AnalyticsSummary summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            JwtAuthenticationToken auth) {
        assertCompanyAdmin(auth);
        UUID companyId = companyId(auth);
        return summaries.summarize(companyId, from, to);
    }

    @PostMapping("/quality-controls/{controlId}/report")
    public PdfGeneratorPort.GeneratedPdf generateReport(
            @PathVariable UUID controlId,
            JwtAuthenticationToken auth) {
        assertCompanyAdmin(auth);
        return reports.execute(controlId);
    }

    private void assertCompanyAdmin(JwtAuthenticationToken auth) {
        if (!"COMPANY_ADMIN".equals(auth.getToken().getClaimAsString("role"))) {
            throw new AccessDeniedException("COMPANY_ADMIN role required");
        }
    }

    private UUID companyId(JwtAuthenticationToken auth) {
        String company = auth.getToken().getClaimAsString("companyId");
        if (company == null) throw new AccessDeniedException("Company context required");
        return UUID.fromString(company);
    }
}
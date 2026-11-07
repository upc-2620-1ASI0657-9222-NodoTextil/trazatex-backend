package com.nodotextil.trazatex.analytics.infrastructure.web;

import com.nodotextil.trazatex.analytics.application.AnalyticsQueryService;
import com.nodotextil.trazatex.analytics.domain.model.AnalyticsSummary;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
class AnalyticsController {
    private final AnalyticsQueryService queries;
    AnalyticsController(AnalyticsQueryService queries) { this.queries=queries; }

    /** Los parámetros se piden opcionales a Spring para que su ausencia o un formato malo sea {@code 400 INVALID_PERIOD} con {@code ApiError}. */
    @GetMapping("/summary")
    AnalyticsSummary summary(@RequestParam(required=false) String from,@RequestParam(required=false) String to,HttpServletRequest request) {
        var actor=RequestActor.from(request);
        actor.requireCompanyAdmin();
        return queries.summary(actor.companyId(),from,to);
    }
}

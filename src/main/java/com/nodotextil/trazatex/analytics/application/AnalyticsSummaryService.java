package com.nodotextil.trazatex.analytics.application;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * PLACEHOLDER added by the integration branch: the real service is missing from every feature branch.
 * It computes nothing and only echoes the query. The analytics owner must replace it with the real implementation.
 */
@Service
public class AnalyticsSummaryService {

    public AnalyticsSummary summarize(UUID companyId, LocalDate from, LocalDate to) {
        return new AnalyticsSummary(companyId, from, to);
    }
}

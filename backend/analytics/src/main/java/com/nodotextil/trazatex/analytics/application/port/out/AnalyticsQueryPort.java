package com.nodotextil.trazatex.analytics.application.port.out;

import com.nodotextil.trazatex.analytics.domain.model.AnalyticsSummary;
import com.nodotextil.trazatex.analytics.domain.model.ReportingPeriod;

import java.util.UUID;

public interface AnalyticsQueryPort {
    AnalyticsSummary.Indicators indicators(UUID company, ReportingPeriod period);
}

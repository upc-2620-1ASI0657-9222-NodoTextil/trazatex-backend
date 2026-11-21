package com.nodotextil.trazatex.analytics.application;

import com.nodotextil.trazatex.analytics.application.port.out.AnalyticsQueryPort;
import com.nodotextil.trazatex.analytics.domain.model.AnalyticsSummary;
import com.nodotextil.trazatex.analytics.domain.model.ReportingPeriod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class AnalyticsQueryService {
    private final AnalyticsQueryPort queries;
    private final Clock clock;
    public AnalyticsQueryService(AnalyticsQueryPort queries, Clock clock) { this.queries=queries; this.clock=clock; }

    @Transactional(readOnly=true)
    public AnalyticsSummary summary(UUID company, String from, String to) {
        ReportingPeriod period = ReportingPeriod.parse(from, to);
        return queries.indicators(company, period).at(period, clock.instant());
    }
}

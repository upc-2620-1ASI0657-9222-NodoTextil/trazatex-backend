package com.nodotextil.trazatex.traceability.infrastructure;

import com.nodotextil.trazatex.organizationaccess.application.contract.OrganizationAccess;
import com.nodotextil.trazatex.traceability.application.GetLineageUseCase;
import com.nodotextil.trazatex.traceability.application.RecordLineageUseCase;
import com.nodotextil.trazatex.traceability.application.GetSharedLineageUseCase;
import com.nodotextil.trazatex.traceability.application.SearchLineageUseCase;
import com.nodotextil.trazatex.traceability.application.AnalyzeFailureImpactUseCase;
import com.nodotextil.trazatex.traceability.application.event.TraceabilityEventPublisher;
import com.nodotextil.trazatex.traceability.domain.FailureImpactAnalyzer;
import java.time.Clock;
import com.nodotextil.trazatex.traceability.application.port.PrivacySettingsPort;
import com.nodotextil.trazatex.traceability.application.port.BatchInfoPort;
import com.nodotextil.trazatex.traceability.domain.LineageRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TraceabilityConfiguration {

    @Bean
    RecordLineageUseCase recordLineageUseCase(LineageRepository lineageRepository) {
        return new RecordLineageUseCase(lineageRepository);
    }

    @Bean
    GetLineageUseCase getLineageUseCase(
            LineageRepository lineageRepository, BatchInfoPort batchInfo) {
        return new GetLineageUseCase(lineageRepository, batchInfo);
    }

    @Bean
    GetSharedLineageUseCase getSharedLineageUseCase(
            GetLineageUseCase getLineageUseCase,
            BatchInfoPort batchInfo,
            PrivacySettingsPort privacySettings,
            OrganizationAccess organizationAccess) {
        return new GetSharedLineageUseCase(getLineageUseCase, batchInfo, privacySettings, organizationAccess);
    }

    @Bean
    SearchLineageUseCase searchLineageUseCase(
            BatchInfoPort batchInfo,
            PrivacySettingsPort privacySettings,
            OrganizationAccess organizationAccess) {
        return new SearchLineageUseCase(batchInfo, privacySettings, organizationAccess);
    }

    @Bean
    AnalyzeFailureImpactUseCase analyzeFailureImpactUseCase(
            LineageRepository lineageRepository,
            BatchInfoPort batchInfo,
            TraceabilityEventPublisher eventPublisher) {
        return new AnalyzeFailureImpactUseCase(
                lineageRepository, batchInfo, new FailureImpactAnalyzer(), eventPublisher, Clock.systemUTC());
    }
}
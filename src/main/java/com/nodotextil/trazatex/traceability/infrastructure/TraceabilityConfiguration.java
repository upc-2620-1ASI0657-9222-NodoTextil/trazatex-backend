package com.nodotextil.trazatex.traceability.infrastructure;

import com.nodotextil.trazatex.traceability.application.RecordLineageUseCase;
import com.nodotextil.trazatex.traceability.domain.LineageRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TraceabilityConfiguration {

    @Bean
    RecordLineageUseCase recordLineageUseCase(LineageRepository lineageRepository) {
        return new RecordLineageUseCase(lineageRepository);
    }
}

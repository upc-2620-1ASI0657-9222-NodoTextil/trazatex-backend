package com.nodotextil.trazatex.analytics.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class AnalyticsConfig {
    @Bean @ConditionalOnMissingBean Clock clock() { return Clock.systemUTC(); }
}

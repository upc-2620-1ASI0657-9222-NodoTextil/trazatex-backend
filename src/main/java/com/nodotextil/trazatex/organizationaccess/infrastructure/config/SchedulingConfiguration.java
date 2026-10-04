package com.nodotextil.trazatex.organizationaccess.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} for the module's jobs. */
@Configuration
@EnableScheduling
class SchedulingConfiguration {
}

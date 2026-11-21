package com.nodotextil.trazatex.quality.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

@ConfigurationProperties("trazatex.evidence")
public record EvidenceProperties(
        @DefaultValue("5") int maxFiles,
        @DefaultValue("10MB") DataSize maxFileSize,
        @DefaultValue UploadQueue upload) {

    public record UploadQueue(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("PT30S") Duration jobDelay,
            @DefaultValue("PT10S") Duration jobInitialDelay,
            @DefaultValue("5") int claimsPerRun,
            @DefaultValue("PT5M") Duration lease,
            @DefaultValue("PT5M") Duration notAvailableDelay,
            @DefaultValue("30") int maxAttempts,
            @DefaultValue("PT30S") Duration initialDelay,
            @DefaultValue("2.0") double multiplier,
            @DefaultValue("PT30M") Duration maxDelay) {
    }
}

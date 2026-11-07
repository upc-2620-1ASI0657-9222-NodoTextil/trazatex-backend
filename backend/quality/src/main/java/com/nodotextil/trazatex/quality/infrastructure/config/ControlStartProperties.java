package com.nodotextil.trazatex.quality.infrastructure.config;

import com.nodotextil.trazatex.quality.application.usecase.PendingCommandSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** El inicio de un control y su recuperación ({@code PendingControlRecoveryJob}). Todo configurable. */
@ConfigurationProperties(prefix = "trazatex.quality.start")
public record ControlStartProperties(
        @DefaultValue("PT30S") Duration grace,
        @DefaultValue("10") int claimsPerRun,
        @DefaultValue("PT2M") Duration lease,
        @DefaultValue("PT15S") Duration initialDelay,
        @DefaultValue("2.0") double multiplier,
        @DefaultValue("PT5M") Duration maxDelay,
        @DefaultValue("20") int errorAfterAttempts,
        @DefaultValue("PT5M") Duration holdWaitPoll,
        @DefaultValue("PT5S") Duration holdJobDelay,
        @DefaultValue("PT3S") Duration holdJobInitialDelay,
        @DefaultValue("PT15S") Duration jobDelay,
        @DefaultValue("PT10S") Duration jobInitialDelay,
        @DefaultValue("true") boolean jobEnabled) {

    public PendingCommandSettings toSettings() {
        return new PendingCommandSettings(grace, claimsPerRun, lease, initialDelay, multiplier, maxDelay, errorAfterAttempts, holdWaitPoll);
    }
}

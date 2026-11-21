package com.nodotextil.trazatex.apigateway.infrastructure.revocation;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

public class RevocationHealthIndicator implements HealthIndicator {

    private final RevokedUsersRegistry registry;

    public RevocationHealthIndicator(RevokedUsersRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Health health() {
        if (registry.isLoaded()) {
            return Health.up().withDetail("inactiveUsers", registry.inactiveCount()).build();
        }
        return Health.outOfService().withDetail("state", "LOADING").build();
    }
}

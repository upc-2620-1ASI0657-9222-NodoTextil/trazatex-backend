package com.nodotextil.trazatex.apigateway.infrastructure.ratelimit;

import com.nodotextil.trazatex.apigateway.GatewayTestSupport;
import com.nodotextil.trazatex.apigateway.infrastructure.config.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {"TRAZATEX_RATE_LIMIT_TRUST_FORWARDED_FOR=true", "TRAZATEX_RATE_LIMIT_TRUSTED_HOPS=2", "test.case=rate-limit-env"})
class RateLimitEnvironmentVariablesTest extends GatewayTestSupport {

    @Autowired
    RateLimitProperties properties;

    @Test
    void theAzureEnvironmentVariablesReachTheRateLimitProperties() {
        assertThat(properties.trustForwardedFor()).isTrue();
        assertThat(properties.trustedHops()).isEqualTo(2);
    }
}

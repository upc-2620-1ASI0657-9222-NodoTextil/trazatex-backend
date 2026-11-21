package com.nodotextil.trazatex.apigateway.application.port.out;

import com.nodotextil.trazatex.apigateway.domain.model.RateLimit;
import com.nodotextil.trazatex.apigateway.domain.model.RateLimitDecision;
import com.nodotextil.trazatex.apigateway.domain.model.RateLimitScope;

public interface RateLimitPort {

    RateLimitDecision tryConsume(RateLimitScope scope, String key, RateLimit limit);

    void refund(RateLimitScope scope, String key, RateLimit limit);
}

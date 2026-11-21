package com.nodotextil.trazatex.apigateway.application.port.in;

import com.nodotextil.trazatex.apigateway.application.dto.RateLimitSubject;
import com.nodotextil.trazatex.apigateway.domain.model.RateLimitDecision;

public interface RateLimitUseCase {

    RateLimitDecision check(RateLimitSubject subject);
}

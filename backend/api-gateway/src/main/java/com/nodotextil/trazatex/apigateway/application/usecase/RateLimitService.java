package com.nodotextil.trazatex.apigateway.application.usecase;

import com.nodotextil.trazatex.apigateway.application.dto.RateLimitSubject;
import com.nodotextil.trazatex.apigateway.application.port.in.RateLimitUseCase;
import com.nodotextil.trazatex.apigateway.application.port.out.RateLimitPort;
import com.nodotextil.trazatex.apigateway.domain.model.AuthenticatedUser;
import com.nodotextil.trazatex.apigateway.domain.model.RateLimit;
import com.nodotextil.trazatex.apigateway.domain.model.RateLimitDecision;
import com.nodotextil.trazatex.apigateway.domain.model.RateLimitPolicy;
import com.nodotextil.trazatex.apigateway.domain.model.RateLimitScope;

public class RateLimitService implements RateLimitUseCase {

    private final RateLimitPort buckets;
    private final RateLimitPolicy policy;

    public RateLimitService(RateLimitPort buckets, RateLimitPolicy policy) {
        this.buckets = buckets;
        this.policy = policy;
    }

    @Override
    public RateLimitDecision check(RateLimitSubject subject) {
        return switch (subject) {
            case RateLimitSubject.Login login -> buckets.tryConsume(RateLimitScope.LOGIN_IP, login.clientKey(), policy.login());
            case RateLimitSubject.Invitation invitation ->
                    buckets.tryConsume(RateLimitScope.INVITATION_IP, invitation.clientKey(), policy.invitation());
            case RateLimitSubject.AuthenticationFailure failure ->
                    buckets.tryConsume(RateLimitScope.AUTH_FAILURE_IP, failure.clientKey(), policy.authFailure());
            case RateLimitSubject.Authenticated authenticated -> checkAuthenticated(authenticated.user());
        };
    }

    private RateLimitDecision checkAuthenticated(AuthenticatedUser user) {
        RateLimit userLimit = policy.user();
        String userKey = user.userId().toString();
        RateLimitDecision byUser = buckets.tryConsume(RateLimitScope.USER, userKey, userLimit);
        if (!byUser.allowed() || user.companyId() == null) {
            return byUser;
        }
        RateLimitDecision byCompany = buckets.tryConsume(RateLimitScope.COMPANY, user.companyId().toString(), policy.company());
        if (!byCompany.allowed()) {
            buckets.refund(RateLimitScope.USER, userKey, userLimit);
        }
        return byCompany;
    }
}

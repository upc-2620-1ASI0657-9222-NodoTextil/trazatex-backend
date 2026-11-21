package com.nodotextil.trazatex.apigateway.infrastructure.security;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSetCacheRefreshEvaluator;
import com.nimbusds.jose.jwk.source.JWKSetSource;
import com.nimbusds.jose.jwk.source.JWKSetSourceWrapper;
import com.nimbusds.jose.jwk.source.RateLimitReachedException;
import com.nimbusds.jose.proc.SecurityContext;

public class StrictRateLimitedJWKSetSource<C extends SecurityContext> extends JWKSetSourceWrapper<C> {

    private final long minIntervalMillis;
    private final long failureRetryMillis;

    private long nextAllowed = Long.MIN_VALUE;
    private boolean loadedOnce;
    private KeySourceException lastFailure;

    public StrictRateLimitedJWKSetSource(JWKSetSource<C> source, long minIntervalMillis, long failureRetryMillis) {
        super(source);
        this.minIntervalMillis = minIntervalMillis;
        this.failureRetryMillis = Math.min(failureRetryMillis, minIntervalMillis);
    }

    @Override
    public synchronized JWKSet getJWKSet(JWKSetCacheRefreshEvaluator refreshEvaluator, long currentTime, C context) throws KeySourceException {
        if (currentTime < nextAllowed) {
            if (!loadedOnce && lastFailure != null) {
                throw lastFailure;
            }
            throw new RateLimitReachedException();
        }
        try {
            JWKSet jwkSet = getSource().getJWKSet(refreshEvaluator, currentTime, context);
            nextAllowed = currentTime + minIntervalMillis;
            loadedOnce = true;
            lastFailure = null;
            return jwkSet;
        } catch (KeySourceException e) {
            nextAllowed = currentTime + (loadedOnce ? minIntervalMillis : failureRetryMillis);
            lastFailure = e;
            throw e;
        }
    }
}

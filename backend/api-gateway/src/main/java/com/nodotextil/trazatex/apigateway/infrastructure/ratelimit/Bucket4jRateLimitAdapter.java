package com.nodotextil.trazatex.apigateway.infrastructure.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.nodotextil.trazatex.apigateway.application.port.out.RateLimitPort;
import com.nodotextil.trazatex.apigateway.domain.model.RateLimit;
import com.nodotextil.trazatex.apigateway.domain.model.RateLimitDecision;
import com.nodotextil.trazatex.apigateway.domain.model.RateLimitScope;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

public class Bucket4jRateLimitAdapter implements RateLimitPort {

    private final Map<RateLimitScope, Cache<String, Bucket>> caches = new EnumMap<>(RateLimitScope.class);
    private final TimeMeter timeMeter;

    public Bucket4jRateLimitAdapter(Duration idleExpiry, long maxEntries, Ticker ticker, TimeMeter timeMeter) {
        this.timeMeter = timeMeter;
        for (RateLimitScope scope : RateLimitScope.values()) {
            caches.put(scope, Caffeine.newBuilder()
                    .expireAfterAccess(idleExpiry)
                    .maximumSize(maxEntries)
                    .executor(Runnable::run)
                    .ticker(ticker)
                    .build());
        }
    }

    public Bucket4jRateLimitAdapter(Duration idleExpiry, long maxEntries) {
        this(idleExpiry, maxEntries, Ticker.systemTicker(), TimeMeter.SYSTEM_NANOTIME);
    }

    @Override
    public RateLimitDecision tryConsume(RateLimitScope scope, String key, RateLimit limit) {
        Bucket bucket = caches.get(scope).get(key, k -> newBucket(limit));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        return probe.isConsumed() ? RateLimitDecision.ALLOWED : RateLimitDecision.rejected(Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }

    @Override
    public void refund(RateLimitScope scope, String key, RateLimit limit) {
        Bucket bucket = caches.get(scope).getIfPresent(key);
        if (bucket != null) {
            bucket.addTokens(1);
        }
    }

    public long size(RateLimitScope scope) {
        Cache<String, Bucket> cache = caches.get(scope);
        cache.cleanUp();
        return cache.estimatedSize();
    }

    public long availableTokens(RateLimitScope scope, String key) {
        Bucket bucket = caches.get(scope).getIfPresent(key);
        return bucket == null ? -1 : bucket.getAvailableTokens();
    }

    private Bucket newBucket(RateLimit limit) {
        return Bucket.builder()
                .addLimit(l -> l.capacity(limit.permits()).refillGreedy(limit.permits(), limit.window()))
                .withCustomTimePrecision(timeMeter)
                .build();
    }
}

package com.nodotextil.trazatex.apigateway.infrastructure.revocation;

import com.nodotextil.trazatex.apigateway.application.port.out.RevokedUsersPort;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class RevokedUsersRegistry implements RevokedUsersPort {

    private final Set<UUID> inactive = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean loaded;

    public RevokedUsersRegistry(boolean loaded) {
        this.loaded = new AtomicBoolean(loaded);
    }

    @Override
    public boolean isLoaded() {
        return loaded.get();
    }

    @Override
    public boolean isRevoked(UUID userId) {
        return inactive.contains(userId);
    }

    void apply(UUID userId, boolean inactiveNow) {
        if (inactiveNow) {
            inactive.add(userId);
        } else {
            inactive.remove(userId);
        }
    }

    void markLoaded() {
        loaded.set(true);
    }

    int inactiveCount() {
        return inactive.size();
    }
}

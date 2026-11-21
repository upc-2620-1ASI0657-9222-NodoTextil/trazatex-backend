package com.nodotextil.trazatex.organizationaccess.application.dto;

import com.nodotextil.trazatex.organizationaccess.domain.model.Ruc;

import java.time.Duration;
import java.time.Instant;

public record CachedRucValidation(Ruc ruc, String businessName, String taxpayerStatus, String domicileCondition, Instant validatedAt) {

    public boolean isFresh(Instant now, Duration ttl) {
        return validatedAt.plus(ttl).isAfter(now);
    }
}

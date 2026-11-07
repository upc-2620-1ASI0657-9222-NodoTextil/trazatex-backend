package com.nodotextil.trazatex.organizationaccess.application.dto;

import com.nodotextil.trazatex.organizationaccess.domain.model.Ruc;

import java.time.Duration;
import java.time.Instant;

/** Fila de {@code validated_ruc_cache} (ADR-012): un RUC que Chequea.pe confirmó ACTIVO y HABIDO. */
public record CachedRucValidation(Ruc ruc, String businessName, String taxpayerStatus, String domicileCondition, Instant validatedAt) {

    public boolean isFresh(Instant now, Duration ttl) {
        return validatedAt.plus(ttl).isAfter(now);
    }
}

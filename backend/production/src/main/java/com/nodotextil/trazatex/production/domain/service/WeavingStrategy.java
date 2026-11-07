package com.nodotextil.trazatex.production.domain.service;

import com.nodotextil.trazatex.production.domain.model.TransformationType;

import java.math.BigDecimal;

/** Estrategia de tejeduría: exige el rendimiento mínimo configurado (por defecto 80 %). */
public final class WeavingStrategy extends MinimumYieldStrategy {

    public WeavingStrategy(BigDecimal minimumYieldPercent) {
        super(TransformationType.WEAVING, "tejeduría", minimumYieldPercent);
    }
}

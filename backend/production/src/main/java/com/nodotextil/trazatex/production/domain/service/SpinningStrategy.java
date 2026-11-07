package com.nodotextil.trazatex.production.domain.service;

import com.nodotextil.trazatex.production.domain.model.TransformationType;

import java.math.BigDecimal;

/** Estrategia de hilandería: exige el rendimiento mínimo configurado (por defecto 60 %). */
public final class SpinningStrategy extends MinimumYieldStrategy {

    public SpinningStrategy(BigDecimal minimumYieldPercent) {
        super(TransformationType.SPINNING, "hilandería", minimumYieldPercent);
    }
}

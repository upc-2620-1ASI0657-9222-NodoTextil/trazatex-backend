package com.nodotextil.trazatex.production.domain.service;

import com.nodotextil.trazatex.production.domain.model.TransformationType;

import java.math.BigDecimal;

/** Estrategia de tintorería: exige el rendimiento mínimo configurado (por defecto 80 %). */
public final class DyeingStrategy extends MinimumYieldStrategy {

    public DyeingStrategy(BigDecimal minimumYieldPercent) {
        super(TransformationType.DYEING, "tintorería", minimumYieldPercent);
    }
}

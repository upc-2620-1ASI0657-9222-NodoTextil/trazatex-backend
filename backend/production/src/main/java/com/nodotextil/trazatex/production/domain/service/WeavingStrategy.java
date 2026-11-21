package com.nodotextil.trazatex.production.domain.service;

import com.nodotextil.trazatex.production.domain.model.TransformationType;

import java.math.BigDecimal;

public final class WeavingStrategy extends MinimumYieldStrategy {

    public WeavingStrategy(BigDecimal minimumYieldPercent) {
        super(TransformationType.WEAVING, "tejeduría", minimumYieldPercent);
    }
}

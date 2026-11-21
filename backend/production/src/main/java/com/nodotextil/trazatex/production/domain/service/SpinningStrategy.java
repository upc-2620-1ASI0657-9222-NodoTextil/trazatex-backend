package com.nodotextil.trazatex.production.domain.service;

import com.nodotextil.trazatex.production.domain.model.TransformationType;

import java.math.BigDecimal;

public final class SpinningStrategy extends MinimumYieldStrategy {

    public SpinningStrategy(BigDecimal minimumYieldPercent) {
        super(TransformationType.SPINNING, "hilandería", minimumYieldPercent);
    }
}

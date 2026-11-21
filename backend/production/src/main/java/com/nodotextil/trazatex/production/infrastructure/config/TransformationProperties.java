package com.nodotextil.trazatex.production.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties("trazatex.transformation")
public record TransformationProperties(MinYieldPercent minYieldPercent) {

    public record MinYieldPercent(BigDecimal spinning, BigDecimal weaving, BigDecimal dyeing) {
    }
}

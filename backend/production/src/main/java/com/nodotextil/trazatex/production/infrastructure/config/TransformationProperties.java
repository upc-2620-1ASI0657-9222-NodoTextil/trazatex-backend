package com.nodotextil.trazatex.production.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Rendimiento mínimo (salida total / entrada total, en %) de cada {@code TransformationStrategy}. Los valores salen de
 * {@code trazatex.transformation.min-yield-percent.*}, que en {@code application.yml} lee variables de entorno con su
 * nombre completo (60 / 80 / 80 por defecto).
 */
@ConfigurationProperties("trazatex.transformation")
public record TransformationProperties(MinYieldPercent minYieldPercent) {

    public record MinYieldPercent(BigDecimal spinning, BigDecimal weaving, BigDecimal dyeing) {
    }
}

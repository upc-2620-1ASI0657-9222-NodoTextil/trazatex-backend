package com.nodotextil.trazatex.analytics.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Respuesta de {@code GET /api/analytics/summary}: los cuatro bloques de EP05 (US37 a US40) de la empresa que consulta. Los nombres de
 * los componentes son los del JSON.
 */
public record AnalyticsSummary(LocalDate from, LocalDate to, String timeZone, List<QualityIndicator> quality,
                               List<FailureCause> failureCauses, List<OperationDuration> durations, MaterialLosses materials, Instant generatedAt) {

    /** US37: lotes por estado de calidad (el estado al cierre del periodo). */
    public record QualityIndicator(String status, long count, BigDecimal percentage) { }

    /** US38: fallas registradas en el periodo, agrupadas por causa. */
    public record FailureCause(String cause, long count) { }

    /** US39: duración promedio, en horas, de las operaciones finalizadas en el periodo. */
    public record OperationDuration(String operation, long count, BigDecimal averageHours) { }

    /** US40: merma y desperdicio acumulados de las transformaciones finalizadas en el periodo. */
    public record MaterialLosses(BigDecimal shrinkageKg, BigDecimal wasteKg, long completedTransformations) { }

    /** Lo que calcula el almacén; {@link #at} le pone el periodo y la hora. */
    public record Indicators(List<QualityIndicator> quality, List<FailureCause> failureCauses, List<OperationDuration> durations, MaterialLosses materials) {
        public AnalyticsSummary at(ReportingPeriod period, Instant generatedAt) {
            return new AnalyticsSummary(period.from(), period.to(), ReportingPeriod.ZONE.getId(), quality, failureCauses, durations, materials, generatedAt);
        }
    }
}

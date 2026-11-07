package com.nodotextil.trazatex.analytics.infrastructure.persistence;

import com.nodotextil.trazatex.analytics.application.port.out.AnalyticsQueryPort;
import com.nodotextil.trazatex.analytics.domain.model.AnalyticsSummary;
import com.nodotextil.trazatex.analytics.domain.model.AnalyticsSummary.*;
import com.nodotextil.trazatex.analytics.domain.model.ReportingPeriod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Los cuatro indicadores de EP05, siempre de {@code company}. Las consultas filtran por las columnas {@code company_id} y {@code business_at}
 * (índice {@code analytics_event_company_type_time}), no por texto dentro del JSON.
 */
@Repository
public class JdbcAnalyticsQueryAdapter implements AnalyticsQueryPort {
    private final JdbcClient jdbc;
    public JdbcAnalyticsQueryAdapter(JdbcClient jdbc) { this.jdbc=jdbc; }

    @Override
    public AnalyticsSummary.Indicators indicators(UUID company, ReportingPeriod period) {
        OffsetDateTime start = period.startInclusive().atOffset(ZoneOffset.UTC), end = period.endExclusive().atOffset(ZoneOffset.UTC);

        // US37: estado de calidad AL CIERRE del periodo. Universo: lotes registrados en el periodo que al cierre pertenecen a la empresa. La dueña
        // es la del BatchPhaseChanged de mayor batchVersion con evento anterior al cierre (los nacimientos y TransferAccepted heredados, versión -1,
        // solo cuentan si no hay ninguno); el estado, el último QualityStatusChanged anterior al cierre.
        var quality = query("""
                WITH scope AS (
                    SELECT b.batch_id,
                        (SELECT q.status FROM batch_quality q WHERE q.batch_id=b.batch_id AND q.occurred_at < :end
                         ORDER BY q.occurred_at DESC,q.event_id DESC LIMIT 1) AS status
                    FROM batch_birth b WHERE b.registered_at >= :start AND b.registered_at < :end
                        AND (SELECT o.company_id FROM batch_owner o WHERE o.batch_id=b.batch_id AND o.occurred_at < :end
                             ORDER BY o.batch_version DESC,o.occurred_at DESC,o.event_id DESC LIMIT 1)=:company)
                SELECT status,count(*) AS count,round(count(*)*100.0/sum(count(*)) OVER(),2) AS percentage
                FROM scope GROUP BY status ORDER BY status
                """,company,start,end).query((rs,n)->new QualityIndicator(rs.getString("status"),rs.getLong("count"),rs.getBigDecimal("percentage"))).list();

        // US38: fallas REGISTRADAS: toda falla que Quality informó (QualityFailureUpdated: abierta, confirmada o resuelta), contada una vez, en el
        // periodo de su primer evento, con la causa que tenía al cierre. Sin causa todavía: «Causa por registrar».
        var failures = query("""
                WITH all_failures AS (
                    SELECT aggregate_id,min(occurred_at) AS created_at FROM analytics_event
                    WHERE event_type='QualityFailureUpdated' AND company_id=:company
                    GROUP BY aggregate_id),
                current_failure AS (
                    SELECT DISTINCT ON(e.aggregate_id) e.aggregate_id,e.payload
                    FROM analytics_event e JOIN all_failures f ON f.aggregate_id=e.aggregate_id
                    WHERE e.event_type='QualityFailureUpdated' AND e.occurred_at < :end
                    AND f.created_at >= :start AND f.created_at < :end
                    ORDER BY e.aggregate_id,e.occurred_at DESC,e.event_id DESC)
                SELECT coalesce(nullif(trim(payload->>'cause'),''),'Causa por registrar') AS cause,
                    count(*) AS count FROM current_failure GROUP BY cause ORDER BY count DESC,cause
                """,company,start,end).query((rs,n)->new FailureCause(rs.getString("cause"),rs.getLong("count"))).list();

        // US39: transformaciones y controles finalizados en el periodo; transferencias ACEPTADAS, solo de la empresa de ORIGEN (company_id de
        // TransferAccepted es fromCompanyId), de su inicio a su aceptación. Las rechazadas no tienen duración.
        var durations = query("""
                WITH durations AS (
                    SELECT 'Transformaciones' AS operation,
                        extract(epoch FROM (business_at-(payload->>'startedAt')::timestamptz))/3600 AS hours
                    FROM analytics_event WHERE event_type='TransformationCompleted' AND company_id=:company
                        AND business_at >= :start AND business_at < :end
                    UNION ALL
                    SELECT 'Controles de calidad',extract(epoch FROM (business_at-(payload->>'startedAt')::timestamptz))/3600
                    FROM analytics_event WHERE event_type='QualityControlCompleted' AND company_id=:company
                        AND business_at >= :start AND business_at < :end
                    UNION ALL
                    SELECT 'Transferencias',extract(epoch FROM (e.business_at-i.occurred_at))/3600
                    FROM analytics_event e JOIN analytics_event i ON i.aggregate_id=e.aggregate_id AND i.event_type='TransferInitiated'
                    WHERE e.event_type='TransferAccepted' AND e.company_id=:company
                        AND e.business_at >= :start AND e.business_at < :end)
                SELECT operation,count(*) AS count,round(avg(hours),3) AS "averageHours"
                FROM durations GROUP BY operation ORDER BY operation
                """,company,start,end).query((rs,n)->new OperationDuration(rs.getString("operation"),rs.getLong("count"),rs.getBigDecimal("averageHours"))).list();

        // US40: merma y desperdicio acumulados de las transformaciones finalizadas en el periodo.
        var materials = query("""
                SELECT coalesce(sum((payload->>'shrinkageKg')::numeric),0) AS "shrinkageKg",
                    coalesce(sum((payload->>'wasteKg')::numeric),0) AS "wasteKg",count(*) AS "completedTransformations"
                FROM analytics_event WHERE event_type='TransformationCompleted' AND company_id=:company
                    AND business_at >= :start AND business_at < :end
                """,company,start,end).query((rs,n)->new MaterialLosses(rs.getBigDecimal("shrinkageKg"),rs.getBigDecimal("wasteKg"),rs.getLong("completedTransformations"))).single();
        return new AnalyticsSummary.Indicators(quality,failures,durations,materials);
    }

    private JdbcClient.StatementSpec query(String sql,UUID company,OffsetDateTime start,OffsetDateTime end) {
        return jdbc.sql(sql).param("company",company).param("start",start).param("end",end);
    }
}

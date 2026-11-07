package com.nodotextil.trazatex.analytics.infrastructure.persistence;

import com.nodotextil.trazatex.analytics.application.port.out.AnalyticsProjectionPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

@Repository
public class JdbcAnalyticsProjectionAdapter implements AnalyticsProjectionPort {
    private final JdbcClient jdbc;
    public JdbcAnalyticsProjectionAdapter(JdbcClient jdbc) { this.jdbc=jdbc; }

    @Override
    public boolean record(RecordedEvent e) {
        return jdbc.sql("""
                INSERT INTO analytics_event(event_id,event_type,aggregate_id,occurred_at,company_id,business_at,payload)
                VALUES(:id,:type,:aggregate,:at,:company,:business,CAST(:payload AS jsonb)) ON CONFLICT DO NOTHING
                """).param("id",e.eventId()).param("type",e.type()).param("aggregate",e.aggregateId()).param("at",e.occurredAt().atOffset(ZoneOffset.UTC))
                .param("company",e.companyId()).param("business",e.businessAt().atOffset(ZoneOffset.UTC)).param("payload",e.payloadJson()).update() > 0;
    }

    @Override
    public void birth(UUID eventId, UUID batchId, UUID companyId, Instant at, String qualityStatus) {
        jdbc.sql("INSERT INTO batch_birth(batch_id,company_id,registered_at) VALUES(:batch,:company,:at) ON CONFLICT DO NOTHING")
                .param("batch",batchId).param("company",companyId).param("at",at.atOffset(ZoneOffset.UTC)).update();
        owner(eventId,batchId,companyId,at,-1);
        quality(eventId,batchId,qualityStatus,at);
    }

    @Override
    public void owner(UUID eventId, UUID batchId, UUID companyId, Instant at, long batchVersion) {
        jdbc.sql("INSERT INTO batch_owner(event_id,batch_id,company_id,occurred_at,batch_version) VALUES(:event,:batch,:company,:at,:version) ON CONFLICT DO NOTHING")
                .param("event",eventId).param("batch",batchId).param("company",companyId).param("at",at.atOffset(ZoneOffset.UTC)).param("version",batchVersion).update();
    }

    @Override
    public void quality(UUID eventId, UUID batchId, String status, Instant at) {
        jdbc.sql("INSERT INTO batch_quality(event_id,batch_id,status,occurred_at) VALUES(:event,:batch,:status,:at) ON CONFLICT DO NOTHING")
                .param("event",eventId).param("batch",batchId).param("status",status).param("at",at.atOffset(ZoneOffset.UTC)).update();
    }
}

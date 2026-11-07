package com.nodotextil.trazatex.analytics;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** V2 sobre una base con datos de la versión 1: llena empresa y fecha de negocio, saca el dueño de los BatchPhaseChanged y crea los índices de M9. */
class AnalyticsMigrationIT {
    @Test
    void v2BackfillsCompanyBusinessDateAndOwnerVersionFromTheStoredEvents() throws Exception {
        try (var database = new PostgreSQLContainer("postgres:16.15").withPassword(UUID.randomUUID().toString())) {
            database.start();
            Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword()).target("1").load().migrate();
            var jdbc = JdbcClient.create(new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword()));
            UUID company = UUID.randomUUID(), destination = UUID.randomUUID(), batch = UUID.randomUUID(), transformation = UUID.randomUUID(), transfer = UUID.randomUUID();
            UUID phaseEvent = UUID.randomUUID(), transferEvent = UUID.randomUUID(), transformationEvent = UUID.randomUUID(), brokenEvent = UUID.randomUUID();
            event(jdbc, transformationEvent, "TransformationCompleted", transformation, "2026-10-10T10:00:05Z",
                    "{\"companyId\":\"" + company + "\",\"startedAt\":\"2026-10-10T08:00:00Z\",\"completedAt\":\"2026-10-10T10:00:00Z\"}");
            event(jdbc, transferEvent, "TransferAccepted", transfer, "2026-10-11T10:00:00Z",
                    "{\"fromCompanyId\":\"" + company + "\",\"toCompanyId\":\"" + destination + "\",\"acceptedAt\":\"2026-10-11T09:59:00Z\"}");
            event(jdbc, phaseEvent, "BatchPhaseChanged", batch, "2026-10-11T10:00:01Z",
                    "{\"batchId\":\"" + batch + "\",\"companyId\":\"" + destination + "\",\"batchVersion\":6}");
            event(jdbc, brokenEvent, "BatchPhaseChanged", batch, "2026-10-11T10:00:02Z", "{\"batchId\":\"no-uuid\",\"companyId\":\"" + destination + "\",\"batchVersion\":\"x\"}");
            // lo que la versión 1 guardó como dueño por TransferAccepted (sin versión) y el nacimiento
            jdbc.sql("INSERT INTO batch_owner(event_id,batch_id,company_id,occurred_at) VALUES(:e,:b,:c,'2026-10-11T10:00:00Z')").param("e", transferEvent).param("b", batch).param("c", destination).update();

            Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword()).load().migrate();

            assertThat(jdbc.sql("SELECT company_id FROM analytics_event WHERE event_id=:e").param("e", transformationEvent).query(UUID.class).single()).isEqualTo(company);
            assertThat(jdbc.sql("SELECT business_at = '2026-10-10T10:00:00Z' FROM analytics_event WHERE event_id=:e").param("e", transformationEvent).query(Boolean.class).single()).isTrue();
            // una transferencia es de la empresa de ORIGEN y se fecha por su aceptación (I8)
            assertThat(jdbc.sql("SELECT company_id FROM analytics_event WHERE event_id=:e").param("e", transferEvent).query(UUID.class).single()).isEqualTo(company);
            assertThat(jdbc.sql("SELECT business_at = '2026-10-11T09:59:00Z' FROM analytics_event WHERE event_id=:e").param("e", transferEvent).query(Boolean.class).single()).isTrue();
            // el dueño sale del BatchPhaseChanged con su versión; el de TransferAccepted sigue con -1 y pierde contra él; el roto no rompe la migración
            assertThat(jdbc.sql("SELECT batch_version FROM batch_owner WHERE event_id=:e").param("e", phaseEvent).query(Long.class).single()).isEqualTo(6L);
            assertThat(jdbc.sql("SELECT batch_version FROM batch_owner WHERE event_id=:e").param("e", transferEvent).query(Long.class).single()).isEqualTo(-1L);
            assertThat(jdbc.sql("SELECT count(*) FROM batch_owner WHERE event_id=:e").param("e", brokenEvent).query(Long.class).single()).isZero();
            assertThat(jdbc.sql("SELECT company_id FROM batch_owner WHERE batch_id=:b ORDER BY batch_version DESC, occurred_at DESC, event_id DESC LIMIT 1").param("b", batch)
                    .query(UUID.class).single()).isEqualTo(destination);
            assertThat(jdbc.sql("SELECT is_nullable FROM information_schema.columns WHERE table_name='analytics_event' AND column_name='business_at'").query(String.class).single()).isEqualTo("NO");

            // M9: con el barrido secuencial desactivado, la consulta de un indicador usa el índice por empresa, tipo y fecha
            try (var connection = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
                 var statement = connection.createStatement()) {
                statement.execute("SET enable_seqscan = off");
                List<String> plan = new ArrayList<>();
                try (var rows = statement.executeQuery("EXPLAIN SELECT * FROM analytics_event WHERE event_type='TransformationCompleted' AND company_id='" + company
                        + "' AND business_at >= '2026-10-01' AND business_at < '2026-11-01'")) {
                    while (rows.next()) plan.add(rows.getString(1));
                }
                assertThat(String.join("\n", plan)).contains("analytics_event_company_type_time");
                plan.clear();
                try (var rows = statement.executeQuery("EXPLAIN SELECT company_id FROM batch_owner WHERE batch_id='" + batch + "' ORDER BY batch_version DESC, occurred_at DESC, event_id DESC LIMIT 1")) {
                    while (rows.next()) plan.add(rows.getString(1));
                }
                assertThat(String.join("\n", plan)).contains("batch_owner_version");
            }
        }
    }

    private static void event(JdbcClient jdbc, UUID id, String type, Object aggregate, String at, String payload) {
        jdbc.sql("INSERT INTO analytics_event(event_id,event_type,aggregate_id,occurred_at,payload) VALUES(:id,:type,:agg,CAST(:at AS timestamptz),CAST(:payload AS jsonb))")
                .param("id", id).param("type", type).param("agg", aggregate.toString()).param("at", at).param("payload", payload).update();
    }
}

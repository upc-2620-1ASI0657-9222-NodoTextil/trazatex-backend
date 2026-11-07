package com.nodotextil.trazatex.analytics.acceptance;

import com.nodotextil.trazatex.analytics.application.AnalyticsProjectionService;
import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import io.cucumber.java.Before;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Entonces;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="trazatex.messaging.enabled=false")
public class IndicatorSteps {
    static final PostgreSQLContainer DB=com.nodotextil.trazatex.analytics.AnalyticsIntegrationTest.DB;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",DB::getJdbcUrl);
        registry.add("spring.datasource.username",DB::getUsername);
        registry.add("spring.datasource.password",DB::getPassword);
    }
    @Autowired AnalyticsProjectionService events;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcClient jdbc;
    @LocalServerPort int port;
    final UUID company=UUID.randomUUID();
    final Instant at=Instant.parse("2026-10-10T10:00:00Z");
    JsonNode result;
    int eventCount;

    @Before public void reset() { jdbc.sql("TRUNCATE analytics_event,batch_birth,batch_owner,batch_quality").update(); }

    void event(String type,UUID aggregate,Object payload,Instant time) {
        String json=mapper.writeValueAsString(BaseDomainEvent.of(type,aggregate.toString(),payload,Clock.fixed(time,ZoneOffset.UTC)));
        events.consume(json);
        events.consume(json);
    }
    @Dado("que existen registros dentro del periodo seleccionado")
    public void quality() {
        UUID first=UUID.randomUUID(), second=UUID.randomUUID();
        event("QualityStatusChanged",first,Map.of("batchId",first,"companyId",company,"newStatus","CONFORMING"),at.plusSeconds(60));
        for(UUID id:List.of(first,second)) event("BatchRegistered",id,Map.of("batchId",id,"companyId",company,"createdAt",at),at);
    }
    @Dado("que existen fallas dentro del periodo seleccionado")
    public void failures() {
        for(String cause:List.of("Fibra corta","Manchas")) {
            UUID id=UUID.randomUUID();
            Map<String,Object> data=Map.of("failureId",id,"batchId",UUID.randomUUID(),"companyId",company,"cause",cause,"status","OPEN");
            event("QualityFailureUpdated",id,data,at);
            event("QualityFailureUpdated",id,data,at.plusSeconds(30));
        }
    }
    @Dado("que existen operaciones finalizadas dentro del periodo seleccionado")
    public void durations() {
        transformation(1,1,at.minusSeconds(7200),at);
        UUID control=UUID.randomUUID(), transfer=UUID.randomUUID();
        event("QualityControlCompleted",control,Map.of("companyId",company,"startedAt",at,"finishedAt",at.plusSeconds(3600)),at.plusSeconds(3600));
        event("TransferInitiated",transfer,Map.of("fromCompanyId",company,"toCompanyId",UUID.randomUUID()),at);
        event("TransferAccepted",transfer,Map.of("fromCompanyId",company,"toCompanyId",UUID.randomUUID(),"acceptedAt",at.plusSeconds(10800),"batchIds",List.of()),at.plusSeconds(10800));
    }
    void transformation(double shrinkage,double waste,Instant start,Instant end) {
        UUID id=UUID.randomUUID();
        event("TransformationCompleted",id,Map.of("companyId",company,"outputs",List.of(Map.of("batchId",UUID.randomUUID())),
                "shrinkageKg",shrinkage,"wasteKg",waste,"startedAt",start,"completedAt",end),end);
    }
    @Dado("que existen transformaciones finalizadas dentro del periodo seleccionado")
    public void materials() {
        transformation(1,1,at.minusSeconds(7200),at);
        transformation(0.5,2,at,at.plusSeconds(3600));
    }
    void query() {
        eventCount=jdbc.sql("SELECT count(*) FROM analytics_event").query(Integer.class).single();
        String json=RestClient.create().get().uri("http://localhost:"+port+"/api/analytics/summary?from=2026-10-01&to=2026-10-31")
                .header("X-User-Id",UUID.randomUUID().toString()).header("X-Company-Id",company.toString())
                .header("X-User-Role","COMPANY_ADMIN").retrieve().body(String.class);
        result=mapper.readTree(json);
    }
    @Cuando("el administrador consulta los indicadores") public void queryQuality() { query(); }
    @Cuando("el administrador consulta el análisis") public void queryFailures() { query(); }
    @Cuando("el administrador consulta sus indicadores") public void queryDurations() { query(); }
    @Cuando("el administrador consulta los indicadores de material") public void queryMaterials() { query(); }
    @Entonces("el sistema muestra cantidades y porcentajes de lotes por estado de calidad")
    public void qualityResult() {
        assertThat(result.path("quality").size()).isEqualTo(2);
        for(var state:result.path("quality")) {
            assertThat(state.path("count").asInt()).isEqualTo(1);
            assertThat(state.path("percentage").asDouble()).isEqualTo(50);
        }
    }
    @Entonces("el sistema muestra la cantidad de fallas y sus causas más frecuentes")
    public void failureResult() {
        assertThat(result.path("failureCauses").size()).isEqualTo(2);
        assertThat(result.path("failureCauses").get(0).path("cause").asText()).isEqualTo("Fibra corta");
        assertThat(result.path("failureCauses").get(0).path("count").asInt()).isEqualTo(1);
    }
    @Entonces("el sistema muestra la duración promedio de transformaciones, transferencias y controles de calidad")
    public void durationResult() {
        Map<String,Double> expected=Map.of("Transformaciones",2.0,"Transferencias",3.0,"Controles de calidad",1.0);
        assertThat(result.path("durations").size()).isEqualTo(3);
        for(var row:result.path("durations")) assertThat(row.path("averageHours").asDouble()).isEqualTo(expected.get(row.path("operation").asText()));
    }
    @Entonces("el sistema muestra la merma acumulada y el desperdicio acumulado")
    public void materialResult() {
        assertThat(result.path("materials").path("shrinkageKg").asDouble()).isEqualTo(1.5);
        assertThat(result.path("materials").path("wasteKg").asDouble()).isEqualTo(3);
        assertThat(result.path("materials").path("completedTransformations").asInt()).isEqualTo(2);
    }
    @Entonces("presenta los resultados del periodo seleccionado.")
    public void periodSelected() { period(); }
    @Entonces("presenta los resultados del periodo.")
    public void periodOnly() { period(); }
    void period() {
        assertThat(result.path("from").asText()).isEqualTo("2026-10-01");
        assertThat(result.path("to").asText()).isEqualTo("2026-10-31");
        assertThat(jdbc.sql("SELECT count(*) FROM analytics_event").query(Integer.class).single()).isEqualTo(eventCount);
    }
}


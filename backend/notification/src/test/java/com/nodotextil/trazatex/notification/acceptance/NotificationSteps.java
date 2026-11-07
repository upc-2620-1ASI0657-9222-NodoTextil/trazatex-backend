package com.nodotextil.trazatex.notification.acceptance;

import com.nodotextil.trazatex.notification.application.NotificationService;
import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Entonces;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"trazatex.messaging.enabled=false","SENDGRID_API_KEY=","SENDGRID_FROM_EMAIL="})
public class NotificationSteps {
    static final PostgreSQLContainer DB=new PostgreSQLContainer("postgres:16.15").withPassword(UUID.randomUUID().toString());
    static { DB.start(); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",DB::getJdbcUrl);
        registry.add("spring.datasource.username",DB::getUsername);
        registry.add("spring.datasource.password",DB::getPassword);
    }
    @Autowired NotificationService notifications;
    @Autowired ObjectMapper mapper;
    @LocalServerPort int port;
    final UUID company=UUID.randomUUID(),batch=UUID.randomUUID(),user=UUID.randomUUID(),failure=UUID.randomUUID();
    String event;
    JsonNode result;
    @Dado("que un lote cambia a Posible falla derivada")
    public void change() {
        event=mapper.writeValueAsString(BaseDomainEvent.of("PossibleDerivedFailureDetected",failure.toString(),
                Map.of("failureId",failure,"sourceBatchId",UUID.randomUUID(),"affected",java.util.List.of(
                        Map.of("batchId",batch,"companyId",company,"commonAncestorBatchId",UUID.randomUUID())))));
    }
    @Cuando("el sistema determina su posible relación con una Falla Confirmada")
    public void consume() { notifications.consume(event); notifications.consume(event); }
    String get(UUID companyId) {
        return RestClient.create().get().uri("http://localhost:"+port+"/api/notifications")
                .header("X-User-Id",user.toString()).header("X-Company-Id",companyId.toString()).header("X-User-Role","COMPANY_ADMIN")
                .retrieve().body(String.class);
    }
    @Entonces("genera una alerta para los administradores responsables")
    public void alert() {
        result=mapper.readTree(get(company));
        assertThat(result.path("items").size()).isEqualTo(1);
        assertThat(result.path("items").get(0).path("role").asText()).isEqualTo("COMPANY_ADMIN");
        assertThat(result.path("items").get(0).path("type").asText()).isEqualTo("POTENTIAL_DERIVED_FAILURE");
        assertThat(mapper.readTree(get(UUID.randomUUID())).path("items").size()).isZero();
        String id=result.path("items").get(0).path("id").asText();
        RestClient.create().put().uri("http://localhost:"+port+"/api/notifications/"+id+"/read")
                .header("X-User-Id",user.toString()).header("X-Company-Id",company.toString()).header("X-User-Role","COMPANY_ADMIN")
                .retrieve().toBodilessEntity();
        assertThat(mapper.readTree(get(company)).path("items").get(0).path("read").asBoolean()).isTrue();
    }
    @Entonces("permite identificar el lote que requiere revisión.")
    public void link() {
        var alert=result.path("items").get(0);
        assertThat(alert.path("resourceType").asText()).isEqualTo("lote");
        assertThat(alert.path("resourceId").asText()).isEqualTo(batch.toString());
        assertThat(alert.path("data").path("batchIds").get(0).asText()).isEqualTo(batch.toString());
        assertThat(alert.path("data").path("failureId").asText()).isEqualTo(failure.toString());
    }
}


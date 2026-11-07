package com.nodotextil.trazatex.notification.application;

import com.nodotextil.trazatex.notification.LogCapture;
import com.nodotextil.trazatex.notification.application.EmailDeliveryService;
import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El consumo sobre PostgreSQL real y con el contexto de Spring (la transacción es la de {@code @Transactional}): la notificación y los
 * correos de sus destinatarios nacen juntos, el destinatario se resuelve al crearlas y el job de envío no recorre las notificaciones.
 */
@SpringBootTest(properties = {"trazatex.messaging.enabled=false", "SENDGRID_API_KEY=", "SENDGRID_FROM_EMAIL=", "trazatex.mail.job-delay=PT1H"})
class NotificationConsumptionIT {
    static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:16.15").withPassword(UUID.randomUUID().toString());
    static { DB.start(); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }

    @Autowired NotificationService notifications;
    @Autowired EmailDeliveryService delivery;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper mapper;

    private String event(String type, String aggregate, Map<String, Object> payload) {
        return mapper.writeValueAsString(BaseDomainEvent.of(type, aggregate, payload));
    }
    private void user(UUID id, UUID company, String role, String email, String status) {
        notifications.consume(event("UserAccountUpdated", id.toString(), Map.of("userId", id.toString(), "companyId", company.toString(),
                "role", role, "email", email, "status", status)));
    }
    private String transfer(UUID transfer, UUID to) {
        return event("TransferInitiated", transfer.toString(), Map.of("transferId", transfer.toString(), "batchIds", List.of(UUID.randomUUID().toString()),
                "fromCompanyId", UUID.randomUUID().toString(), "toCompanyId", to.toString()));
    }
    private long emails(String recipient) {
        return jdbc.sql("SELECT count(*) FROM deferred_email WHERE recipient=:r").param("r", recipient).query(Long.class).single();
    }

    @Test void theNotificationAndTheEmailsOfItsRecipientsAreCreatedInTheSameTransaction() {
        UUID company = UUID.randomUUID(), admin = UUID.randomUUID(), operator = UUID.randomUUID(), inactive = UUID.randomUUID(), other = UUID.randomUUID();
        user(admin, company, "COMPANY_ADMIN", "admin-" + admin + "@correo.controlado", "ACTIVE");
        user(operator, company, "OPERATOR", "operario-" + operator + "@correo.controlado", "ACTIVE");
        user(inactive, company, "COMPANY_ADMIN", "inactivo-" + inactive + "@correo.controlado", "INACTIVE");
        user(other, UUID.randomUUID(), "COMPANY_ADMIN", "otra-" + other + "@correo.controlado", "ACTIVE");
        UUID transfer = UUID.randomUUID();

        notifications.consume(transfer(transfer, company));

        assertThat(jdbc.sql("SELECT count(*) FROM notification WHERE resource_id=:t").param("t", transfer.toString()).query(Long.class).single()).isEqualTo(1);
        assertThat(emails("admin-" + admin + "@correo.controlado")).isEqualTo(1);
        assertThat(emails("operario-" + operator + "@correo.controlado")).isZero();
        assertThat(emails("inactivo-" + inactive + "@correo.controlado")).isZero();
        assertThat(emails("otra-" + other + "@correo.controlado")).isZero();
    }

    @Test void ifTheEmailCannotBeCreatedTheNotificationAndTheProcessedMarkAreRolledBackToo() {
        UUID company = UUID.randomUUID(), admin = UUID.randomUUID(), transfer = UUID.randomUUID();
        user(admin, company, "COMPANY_ADMIN", "falla-" + admin + "@correo.controlado", "ACTIVE");
        jdbc.sql("""
                CREATE OR REPLACE FUNCTION reject_email() RETURNS trigger AS $$ BEGIN
                    IF NEW.recipient LIKE 'falla-%' THEN RAISE EXCEPTION 'correo rechazado por la prueba'; END IF; RETURN NEW; END $$ LANGUAGE plpgsql
                """).update();
        jdbc.sql("CREATE TRIGGER reject_email BEFORE INSERT ON deferred_email FOR EACH ROW EXECUTE FUNCTION reject_email()").update();
        try {
            String json = transfer(transfer, company);
            assertThatThrownBy(() -> notifications.consume(json)).hasMessageContaining("correo rechazado");
            assertThat(jdbc.sql("SELECT count(*) FROM notification WHERE resource_id=:t").param("t", transfer.toString()).query(Long.class).single()).isZero();
            assertThat(jdbc.sql("SELECT count(*) FROM processed_events WHERE event_type='TransferInitiated' AND event_id=CAST(:e AS uuid)")
                    .param("e", mapper.readTree(json).path("eventId").asText()).query(Long.class).single()).isZero();
        } finally {
            jdbc.sql("DROP TRIGGER reject_email ON deferred_email").update();
        }
    }

    @Test void theRecipientIsResolvedWhenTheNotificationIsCreatedNotWhenTheJobRuns() {
        UUID company = UUID.randomUUID(), early = UUID.randomUUID(), late = UUID.randomUUID(), reactivated = UUID.randomUUID();
        user(early, company, "COMPANY_ADMIN", "temprano-" + early + "@correo.controlado", "ACTIVE");
        user(reactivated, company, "COMPANY_ADMIN", "reactivado-" + reactivated + "@correo.controlado", "INACTIVE");
        notifications.consume(transfer(UUID.randomUUID(), company));

        user(late, company, "COMPANY_ADMIN", "tardio-" + late + "@correo.controlado", "ACTIVE");
        user(reactivated, company, "COMPANY_ADMIN", "reactivado-" + reactivated + "@correo.controlado", "ACTIVE");
        delivery.deliver();
        delivery.deliver();

        assertThat(emails("temprano-" + early + "@correo.controlado")).isEqualTo(1);
        assertThat(emails("tardio-" + late + "@correo.controlado")).isZero();
        assertThat(emails("reactivado-" + reactivated + "@correo.controlado")).isZero();
    }

    @Test void theDeliveryJobDoesNotCreateEmailsFromNotificationsThatAlreadyExist() {
        UUID company = UUID.randomUUID(), admin = UUID.randomUUID();
        user(admin, company, "COMPANY_ADMIN", "sin-correo-" + admin + "@correo.controlado", "ACTIVE");
        jdbc.sql("""
                INSERT INTO notification(id,event_id,company_id,recipient_role,type,title,message,created_at)
                VALUES(gen_random_uuid(),gen_random_uuid(),CAST(:c AS uuid),'COMPANY_ADMIN','TRANSFER_RECEIVED','t','m',:at)
                """).param("c", company.toString()).param("at", OffsetDateTime.now().plusMinutes(1)).update();
        delivery.deliver();
        assertThat(emails("sin-correo-" + admin + "@correo.controlado")).isZero();
    }

    @Test void aRedeliveredEventNeitherDuplicatesTheNotificationNorTheEmail() {
        UUID company = UUID.randomUUID(), admin = UUID.randomUUID(), transfer = UUID.randomUUID();
        user(admin, company, "COMPANY_ADMIN", "unico-" + admin + "@correo.controlado", "ACTIVE");
        String json = transfer(transfer, company);
        notifications.consume(json);
        notifications.consume(json);
        assertThat(jdbc.sql("SELECT count(*) FROM notification WHERE resource_id=:t").param("t", transfer.toString()).query(Long.class).single()).isEqualTo(1);
        assertThat(emails("unico-" + admin + "@correo.controlado")).isEqualTo(1);
    }

    @Test void theInvitationTokenIsOnlyKeptWhilePendingAndNeverInTheNotificationTables() {
        String token = "tok-" + UUID.randomUUID();
        notifications.consume(event("InvitationCreated", UUID.randomUUID().toString(), Map.of("email", "invitado-" + token + "@correo.controlado",
                "acceptUrl", "https://app.trazatex.example/invitations/" + token, "expiresAt", java.time.Instant.now().plusSeconds(86400).toString())));
        assertThat(jdbc.sql("SELECT body FROM deferred_email WHERE recipient=:r").param("r", "invitado-" + token + "@correo.controlado").query(String.class).single()).contains(token);
        assertThat(jdbc.sql("SELECT count(*) FROM notification WHERE message LIKE :t OR data::text LIKE :t").param("t", "%" + token + "%").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM processed_events WHERE event_type LIKE :t").param("t", "%" + token + "%").query(Long.class).single()).isZero();
    }

    // ---- cierre de la fase 07 (I19): orden, usuario INACTIVE, directorio incompleto, secretos fuera de los logs ----

    private void userAt(UUID id, UUID company, String role, String email, String status, Instant at) {
        notifications.consume(mapper.writeValueAsString(BaseDomainEvent.of("UserAccountUpdated", id.toString(),
                Map.of("userId", id.toString(), "companyId", company.toString(), "role", role, "email", email, "status", status), Clock.fixed(at, ZoneOffset.UTC))));
    }
    private String status(UUID id) {
        return jdbc.sql("SELECT status FROM user_projection WHERE user_id=:u").param("u", id).query(String.class).single();
    }
    private long notificationsOf(UUID transfer) {
        return jdbc.sql("SELECT count(*) FROM notification WHERE resource_id=:t").param("t", transfer.toString()).query(Long.class).single();
    }

    /** Fuera de orden: una actualización del usuario que llega tarde (más vieja) no deshace la más nueva que ya se aplicó. */
    @Test void aUserUpdateThatArrivesLateDoesNotUndoANewerOne() {
        UUID company = UUID.randomUUID(), admin = UUID.randomUUID();
        Instant earlier = Instant.parse("2026-01-10T10:00:00Z"), later = Instant.parse("2026-01-10T11:00:00Z");
        String email = "orden-" + admin + "@correo.controlado";
        userAt(admin, company, "COMPANY_ADMIN", email, "INACTIVE", later);      // lo más nuevo llega primero
        userAt(admin, company, "COMPANY_ADMIN", email, "ACTIVE", earlier);      // lo viejo llega después
        assertThat(status(admin)).as("gana la más nueva, no la última en llegar").isEqualTo("INACTIVE");

        notifications.consume(transfer(UUID.randomUUID(), company));
        assertThat(emails(email)).as("sigue inactivo: no recibe").isZero();

        // y al revés: lo nuevo que llega después sí se aplica
        userAt(admin, company, "COMPANY_ADMIN", email, "ACTIVE", later.plusSeconds(60));
        assertThat(status(admin)).isEqualTo("ACTIVE");
        notifications.consume(transfer(UUID.randomUUID(), company));
        assertThat(emails(email)).isEqualTo(1);
    }

    /** Usuario INACTIVE: no recibe correo aunque haya sido activo; si se reactiva, recibe los siguientes pero no los de antes. */
    @Test void aDeactivatedUserReceivesNoEmailAndAReactivatedOneOnlyTheNextOnes() {
        UUID company = UUID.randomUUID(), admin = UUID.randomUUID();
        Instant t0 = Instant.parse("2026-02-01T08:00:00Z");
        String email = "baja-" + admin + "@correo.controlado";
        userAt(admin, company, "COMPANY_ADMIN", email, "ACTIVE", t0);
        notifications.consume(transfer(UUID.randomUUID(), company));
        assertThat(emails(email)).isEqualTo(1);

        userAt(admin, company, "COMPANY_ADMIN", email, "INACTIVE", t0.plusSeconds(60));
        UUID whileInactive = UUID.randomUUID();
        notifications.consume(transfer(whileInactive, company));
        assertThat(notificationsOf(whileInactive)).as("la notificación dentro de la aplicación sí se crea (es de la empresa)").isEqualTo(1);
        assertThat(emails(email)).as("pero no hay correo nuevo para el inactivo").isEqualTo(1);

        userAt(admin, company, "COMPANY_ADMIN", email, "ACTIVE", t0.plusSeconds(120));
        notifications.consume(transfer(UUID.randomUUID(), company));
        assertThat(emails(email)).as("reactivado: recibe el siguiente, no el que se perdió").isEqualTo(2);
    }

    /** Directorio incompleto: sin usuarios conocidos no hay a quién escribir, pero el hecho no se pierde ni falla el consumo. */
    @Test void withAnIncompleteDirectoryTheNotificationIsKeptNoEmailIsMadeAndNothingIsSentLater() {
        UUID company = UUID.randomUUID(), transfer = UUID.randomUUID(), operator = UUID.randomUUID(), admin = UUID.randomUUID();
        String event = transfer(transfer, company);

        notifications.consume(event);                                           // la empresa aún no está en el directorio

        assertThat(notificationsOf(transfer)).as("la notificación dentro de la aplicación existe").isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM deferred_email WHERE event_id=(SELECT event_id FROM notification WHERE resource_id=:t)")
                .param("t", transfer.toString()).query(Long.class).single()).isZero();
        user(operator, company, "OPERATOR", "operario-" + operator + "@correo.controlado", "ACTIVE");     // solo un rol que no recibe
        user(admin, company, "COMPANY_ADMIN", "tarde-" + admin + "@correo.controlado", "ACTIVE");          // el directorio llega después
        notifications.consume(event);                                                                       // reentrega del mismo evento
        assertThat(emails("tarde-" + admin + "@correo.controlado")).as("el directorio tardío no escribe retroactivamente").isZero();
        assertThat(notificationsOf(transfer)).isEqualTo(1);
    }

    /** Un administrador sin correo conocido no genera un correo a «» ni rompe el consumo. */
    @Test void anAdministratorWithoutAnEmailGeneratesNoEmailRow() {
        UUID company = UUID.randomUUID(), admin = UUID.randomUUID(), transfer = UUID.randomUUID();
        user(admin, company, "COMPANY_ADMIN", "   ", "ACTIVE");

        notifications.consume(transfer(transfer, company));

        assertThat(notificationsOf(transfer)).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM deferred_email WHERE btrim(recipient)=''").query(Long.class).single()).isZero();
    }

    /** El token de una invitación vive solo en el cuerpo del correo pendiente; no aparece en ningún log, ni al consumir ni al intentar entregar. */
    @Test void theInvitationTokenNeverShowsUpInTheLogs() {
        String token = "tok-" + UUID.randomUUID() + "-un-solo-uso";
        String acceptUrl = "https://app.trazatex.example/invitations/" + token;
        UUID invitation = UUID.randomUUID();
        try (LogCapture logs = new LogCapture()) {
            logs.mark("captura-activa");
            notifications.consume(event("InvitationCreated", invitation.toString(), Map.of("invitationId", invitation.toString(),
                    "email", "invitado-" + invitation + "@correo.controlado", "acceptUrl", acceptUrl,
                    "expiresAt", java.time.Instant.now().plus(java.time.Duration.ofDays(2)).toString())));
            delivery.deliver();                                                 // sin proveedor configurado: no se reclama ni se envía

            assertThat(logs.lines()).as("la captura está viva").contains("captura-activa");
            assertThat(logs.lines()).noneMatch(line -> line.contains(token) || line.contains(acceptUrl));
        }
        assertThat(jdbc.sql("SELECT count(*) FROM deferred_email WHERE recipient=:r AND body LIKE :t")
                .param("r", "invitado-" + invitation + "@correo.controlado").param("t", "%" + token + "%").query(Long.class).single())
                .as("el token solo está en el correo pendiente").isEqualTo(1);
    }
}

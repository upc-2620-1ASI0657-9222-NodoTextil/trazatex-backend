package com.nodotextil.trazatex.notification.infrastructure.external;

import com.nodotextil.trazatex.notification.LogCapture;
import com.nodotextil.trazatex.notification.application.EmailDeliveryService;
import com.nodotextil.trazatex.notification.application.RecipientPolicy;
import com.nodotextil.trazatex.notification.infrastructure.persistence.JdbcDeferredEmailStoreAdapter;
import com.sun.net.httpserver.HttpServer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** La entrega completa —servicio, adaptador JDBC, SendGrid contra un servidor local— sobre PostgreSQL real. */
class EmailDeliveryIT {
    private static final String SECRET = "https://app.trazatex.example/invitations/token-de-un-solo-uso";

    @Test
    void blocksReservedDomainsSendsTheRestAndNeverKeepsABodyAfterTheEmailIsFinished() throws Exception {
        try (var database = new PostgreSQLContainer("postgres:16.15").withPassword(UUID.randomUUID().toString())) {
            database.start();
            Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword()).load().migrate();
            var jdbc = JdbcClient.create(new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword()));
            var requests = new AtomicInteger();
            var payload = new AtomicReference<String>();
            var response = new AtomicInteger(202);
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v3/mail/send", exchange -> {
                requests.incrementAndGet();
                payload.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(response.get(), -1);
                exchange.close();
            });
            server.start();
            try {
                var sender = new SendGridEmailSender("local-http-stub", "remitente@trazatex.example", "http://127.0.0.1:" + server.getAddress().getPort());
                var delivery = new EmailDeliveryService(new JdbcDeferredEmailStoreAdapter(jdbc), sender, new RecipientPolicy(" UPC.PE , escuela.example "), Clock.systemUTC(), 3);

                for (String email : new String[]{"carlos.r@upc.pe", "MARTIN.S@UPC.PE", "persona@pima.example.com", "persona@example.org", "persona@empresa.invalid"}) enqueue(jdbc, email);
                delivery.deliver();
                assertThat(requests.get()).isZero();
                assertThat(count(jdbc, "status='UNDELIVERABLE' AND attempts=0 AND accepted_at IS NULL AND body=''")).isEqualTo(5);

                enqueue(jdbc, "destinatario@correo.controlado");
                delivery.deliver();
                assertThat(requests.get()).isEqualTo(1);
                assertThat(payload.get()).contains("TrazaTex", "destinatario@correo.controlado");
                assertThat(count(jdbc, "status='SENT' AND accepted_at IS NOT NULL AND body=''")).isEqualTo(1);

                // 503: sigue PENDING con su cuerpo, un intento y el motivo; no se vuelve a enviar antes de su espera
                response.set(503);
                enqueue(jdbc, "pendiente@correo.controlado");
                delivery.deliver();
                assertThat(requests.get()).isEqualTo(2);
                assertThat(row(jdbc, "pendiente@correo.controlado", "status")).isEqualTo("PENDING");
                assertThat(jdbc.sql("SELECT accepted_at IS NULL AND attempts=1 AND last_error='SENDGRID_HTTP_503' AND body LIKE '%token-de-un-solo-uso%' AND next_attempt_at > now() FROM deferred_email WHERE recipient='pendiente@correo.controlado'").query(Boolean.class).single()).isTrue();
                delivery.deliver();
                assertThat(requests.get()).isEqualTo(2);

                // 401: permanente, FAILED al primer intento, sin cuerpo y sin reintentos
                response.set(401);
                enqueue(jdbc, "rechazado@correo.controlado");
                delivery.deliver();
                assertThat(requests.get()).isEqualTo(3);
                assertThat(jdbc.sql("SELECT status='FAILED' AND attempts=1 AND last_error='SENDGRID_HTTP_401' AND body='' FROM deferred_email WHERE recipient='rechazado@correo.controlado'").query(Boolean.class).single()).isTrue();
                delivery.deliver();
                assertThat(requests.get()).isEqualTo(3);

                // el reintento está acotado: con 3 intentos máximos, el tercero fallido pasa a FAILED
                response.set(503);
                for (int attempt = 2; attempt <= 3; attempt++) {
                    jdbc.sql("UPDATE deferred_email SET next_attempt_at=now() WHERE recipient='pendiente@correo.controlado'").update();
                    delivery.deliver();
                }
                assertThat(jdbc.sql("SELECT status='FAILED' AND attempts=3 AND last_error LIKE 'REINTENTOS_AGOTADOS: SENDGRID_HTTP_503' AND body='' FROM deferred_email WHERE recipient='pendiente@correo.controlado'").query(Boolean.class).single()).isTrue();

                // una invitación vencida pasa a EXPIRED sin enviarse y sin conservar su token
                jdbc.sql("INSERT INTO deferred_email(id,event_id,recipient,subject,body,expires_at) VALUES(gen_random_uuid(),gen_random_uuid(),'vencida@correo.controlado','Invitación a TrazaTex',:body,now() - interval '1 minute')")
                        .param("body", SECRET).update();
                int before = requests.get();
                delivery.deliver();
                assertThat(requests.get()).isEqualTo(before);
                assertThat(jdbc.sql("SELECT status='EXPIRED' AND body='' FROM deferred_email WHERE recipient='vencida@correo.controlado'").query(Boolean.class).single()).isTrue();

                // ningún correo terminado guarda el secreto
                assertThat(jdbc.sql("SELECT count(*) FROM deferred_email WHERE status<>'PENDING' AND body<>''").query(Long.class).single()).isZero();
            } finally {
                server.stop(0);
            }
        }
    }

    @Test
    void twoJobsRunningAtTheSameTimeNeverSendTheSameEmailTwice() throws Exception {
        try (var database = new PostgreSQLContainer("postgres:16.15").withPassword(UUID.randomUUID().toString())) {
            database.start();
            Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword()).load().migrate();
            var jdbc = JdbcClient.create(new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword()));
            var requests = new AtomicInteger();
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v3/mail/send", exchange -> {
                requests.incrementAndGet();
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(202, -1);
                exchange.close();
            });
            server.start();
            try {
                var sender = new SendGridEmailSender("local-http-stub", "remitente@trazatex.example", "http://127.0.0.1:" + server.getAddress().getPort());
                var store = new JdbcDeferredEmailStoreAdapter(jdbc);
                for (int i = 0; i < 6; i++) enqueue(jdbc, "persona" + i + "@correo.controlado");
                var first = new EmailDeliveryService(store, sender, new RecipientPolicy(""), Clock.systemUTC(), 3);
                var second = new EmailDeliveryService(store, sender, new RecipientPolicy(""), Clock.systemUTC(), 3);
                var threads = new Thread[]{new Thread(first::deliver), new Thread(second::deliver)};
                for (var thread : threads) thread.start();
                for (var thread : threads) thread.join();
                assertThat(requests.get()).isEqualTo(6);
                assertThat(count(jdbc, "status='SENT'")).isEqualTo(6);
            } finally {
                server.stop(0);
            }
        }
    }

    /**
     * SendGrid caído y de vuelta (I19): el correo queda PENDING con su cuerpo y su espera; cuando el proveedor se recupera y vence la espera, el
     * siguiente ciclo lo envía <b>una sola vez</b>, borra el cuerpo y no lo vuelve a mandar. Ningún log lleva el secreto del cuerpo ni la clave.
     */
    @Test
    void anEmailLeftPendingByAnOutageIsSentOnceWhenTheProviderIsBackAndNoSecretReachesTheLogs() throws Exception {
        try (var database = new PostgreSQLContainer("postgres:16.15").withPassword(UUID.randomUUID().toString());
             var logs = new LogCapture()) {
            database.start();
            logs.mark("captura-activa");
            Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword()).load().migrate();
            var jdbc = JdbcClient.create(new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword()));
            var requests = new AtomicInteger();
            var response = new AtomicInteger(503);
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v3/mail/send", exchange -> {
                requests.incrementAndGet();
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(response.get(), -1);
                exchange.close();
            });
            server.start();
            try {
                var sender = new SendGridEmailSender("local-http-stub-clave", "remitente@trazatex.example", "http://127.0.0.1:" + server.getAddress().getPort());
                var delivery = new EmailDeliveryService(new JdbcDeferredEmailStoreAdapter(jdbc), sender, new RecipientPolicy(""), Clock.systemUTC(), 5);
                enqueue(jdbc, "caida@correo.controlado");

                delivery.deliver();                                              // el proveedor está caído
                assertThat(row(jdbc, "caida@correo.controlado", "status")).isEqualTo("PENDING");
                assertThat(requests.get()).isEqualTo(1);

                response.set(202);                                               // el proveedor se recupera
                delivery.deliver();
                assertThat(requests.get()).as("aún no vence la espera: no se llama").isEqualTo(1);
                jdbc.sql("UPDATE deferred_email SET next_attempt_at=now() WHERE recipient='caida@correo.controlado'").update();
                delivery.deliver();

                assertThat(requests.get()).isEqualTo(2);
                assertThat(count(jdbc, "recipient='caida@correo.controlado' AND status='SENT' AND accepted_at IS NOT NULL AND body='' AND last_error IS NULL AND attempts=1")).as("SENT: solo cuenta el fallo anterior y borra el motivo y el cuerpo").isEqualTo(1);
                delivery.deliver();
                assertThat(requests.get()).as("una vez enviado no se manda otra vez").isEqualTo(2);
                assertThat(logs.lines()).as("la captura está viva").contains("captura-activa");
                assertThat(logs.lines()).noneMatch(line -> line.contains("token-de-un-solo-uso") || line.contains("local-http-stub-clave"));
            } finally {
                server.stop(0);
            }
        }
    }

    private static long count(JdbcClient jdbc, String where) {
        return jdbc.sql("SELECT count(*) FROM deferred_email WHERE " + where).query(Long.class).single();
    }
    private static String row(JdbcClient jdbc, String recipient, String column) {
        return jdbc.sql("SELECT " + column + " FROM deferred_email WHERE recipient=:r").param("r", recipient).query(String.class).single();
    }
    private static void enqueue(JdbcClient jdbc, String email) {
        jdbc.sql("INSERT INTO deferred_email(id,event_id,recipient,subject,body) VALUES(:id,:event,:email,'Invitación a TrazaTex',:body)")
                .param("id", UUID.randomUUID()).param("event", UUID.randomUUID()).param("email", email).param("body", SECRET).update();
    }
}

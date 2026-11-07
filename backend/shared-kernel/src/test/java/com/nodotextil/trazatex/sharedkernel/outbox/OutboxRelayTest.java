package com.nodotextil.trazatex.sharedkernel.outbox;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nodotextil.trazatex.sharedkernel.testsupport.MutableClock;
import com.nodotextil.trazatex.sharedkernel.testsupport.RecordingTransactionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.nodotextil.trazatex.sharedkernel.outbox.RecordingEventPublisher.message;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxRelayTest {

    private static final Instant T0 = Instant.parse("2026-10-19T14:00:00Z");
    private static final String BATCH_TOPIC = "production.batch";

    private InMemoryOutboxStore store;
    private RecordingEventPublisher publisher;
    private RecordingTransactionManager transactionManager;
    private MutableClock clock;
    private OutboxRelay relay;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(OutboxRelay.class)).addAppender(logs);
        store = new InMemoryOutboxStore();
        publisher = new RecordingEventPublisher();
        transactionManager = new RecordingTransactionManager();
        clock = new MutableClock(Instant.parse("2026-10-19T14:05:00Z"));
        relay = new OutboxRelay(store, publisher, transactionManager, clock, 100);
    }

    @AfterEach
    void detachLogs() {
        ((Logger) LoggerFactory.getLogger(OutboxRelay.class)).detachAppender(logs);
    }

    @Test
    void publicaEnOrdenDeCreatedAtConKeyAggregateIdYMarcaCadaFila() {
        OutboxMessage m1 = seed("b-1", "{\"n\":1}", 0);
        OutboxMessage m2 = seed("b-2", "{\"n\":2}", 1);
        OutboxMessage m3 = seed("b-1", "{\"n\":3}", 2);

        OutboxRelay.Result result = relay.publishPending();

        assertThat(result).isEqualTo(new OutboxRelay.Result(3, 0, 0));
        assertThat(publisher.published()).containsExactly(
                new RecordingEventPublisher.Published(BATCH_TOPIC, "b-1", "{\"n\":1}"),
                new RecordingEventPublisher.Published(BATCH_TOPIC, "b-2", "{\"n\":2}"),
                new RecordingEventPublisher.Published(BATCH_TOPIC, "b-1", "{\"n\":3}"));
        assertThat(List.of(m1, m2, m3)).allSatisfy(m -> {
            assertThat(store.isPublished(m.id())).isTrue();
            assertThat(store.publishedAt(m.id())).isEqualTo(Instant.parse("2026-10-19T14:05:00Z"));
        });
        assertThat(transactionManager.outcomes()).containsExactly("commit");
    }

    @Test
    void ordenaPorCreatedAtAunqueElAlmacenLasDevuelvaDesordenadas() {
        seed("b-1", "tercero", 3);
        seed("b-1", "primero", 1);
        seed("b-1", "segundo", 2);

        relay.publishPending();

        assertThat(publisher.payloads()).containsExactly("primero", "segundo", "tercero");
    }

    @Test
    void marcaComoPublicadaSoloDespuesDeLaConfirmacionDelBroker() {
        OutboxMessage m = seed("b-1", "{}", 0);
        List<Boolean> publishedBeforeConfirm = new ArrayList<>();
        publisher.beforeConfirm(p -> publishedBeforeConfirm.add(store.isPublished(m.id())));

        relay.publishPending();

        assertThat(publishedBeforeConfirm).containsExactly(false);
        assertThat(store.isPublished(m.id())).isTrue();
    }

    @Test
    void siElBrokerNoConfirmaLaFilaSigueSinPublicarYSeReintenta() {
        OutboxMessage m = seed("b-1", "{}", 0);
        publisher.failFor(BATCH_TOPIC, "b-1");

        OutboxRelay.Result failed = relay.publishPending();

        assertThat(failed).isEqualTo(new OutboxRelay.Result(0, 1, 0));
        assertThat(store.isPublished(m.id())).isFalse();
        assertThat(publisher.published()).isEmpty();

        publisher.recover();
        OutboxRelay.Result retried = relay.publishPending();

        assertThat(retried).isEqualTo(new OutboxRelay.Result(1, 0, 0));
        assertThat(store.isPublished(m.id())).isTrue();
    }

    @Test
    void unaFallaNoAdelantaALosPosterioresDelMismoAgregadoPeroSiPublicaLosDeOtros() {
        OutboxMessage a1 = seed("b-1", "a1", 0);
        OutboxMessage b1 = seed("b-2", "b1", 1);
        OutboxMessage a2 = seed("b-1", "a2", 2);
        OutboxMessage b2 = seed("b-2", "b2", 3);
        publisher.failFor(BATCH_TOPIC, "b-1");

        OutboxRelay.Result result = relay.publishPending();

        assertThat(result).isEqualTo(new OutboxRelay.Result(2, 1, 1));
        assertThat(publisher.payloads()).containsExactly("b1", "b2");
        assertThat(store.isPublished(a1.id())).isFalse();
        assertThat(store.isPublished(a2.id())).isFalse();
        assertThat(store.isPublished(b1.id())).isTrue();
        assertThat(store.isPublished(b2.id())).isTrue();
        assertThat(transactionManager.outcomes()).containsExactly("commit"); // las marcas ya hechas se conservan

        publisher.recover();
        relay.publishPending();

        assertThat(publisher.payloads()).containsExactly("b1", "b2", "a1", "a2");
    }

    @Test
    void elMismoAgregadoEnOtroTopicoNoSeBloquea() {
        seed(BATCH_TOPIC, "b-1", "lote", 0);
        seed("production.transformation", "b-1", "transformacion", 1);
        publisher.failFor(BATCH_TOPIC, "b-1");

        OutboxRelay.Result result = relay.publishPending();

        assertThat(result).isEqualTo(new OutboxRelay.Result(1, 1, 0));
        assertThat(publisher.payloads()).containsExactly("transformacion");
    }

    @Test
    void sinPendientesNoHaceNada() {
        assertThat(relay.publishPending()).isEqualTo(new OutboxRelay.Result(0, 0, 0));
        assertThat(publisher.published()).isEmpty();
    }

    @Test
    void respetaElTamanoDeLote() {
        relay = new OutboxRelay(store, publisher, transactionManager, clock, 2);
        for (int i = 0; i < 5; i++) {
            seed("b-" + i, "m" + i, i);
        }

        assertThat(relay.publishPending().published()).isEqualTo(2);
        assertThat(relay.publishPending().published()).isEqualTo(2);
        assertThat(relay.publishPending().published()).isEqualTo(1);
        assertThat(publisher.payloads()).containsExactly("m0", "m1", "m2", "m3", "m4");
        assertThat(store.pendingCount()).isZero();
    }

    @Test
    void siFallaElMarcadoLaTransaccionSeRevierteYElMensajeSePublicaDeNuevoAtLeastOnce() {
        OutboxMessage m = seed("b-1", "{}", 0);
        store.failMarkPublishedWith(new IllegalStateException("base caída"));

        assertThatThrownBy(relay::publishPending).isInstanceOf(IllegalStateException.class);

        assertThat(transactionManager.outcomes()).containsExactly("rollback");
        assertThat(store.isPublished(m.id())).isFalse();
        assertThat(publisher.payloads()).containsExactly("{}"); // ya salió hacia Kafka: el duplicado es esperado

        store.failMarkPublishedWith(null);
        relay.publishPending();

        assertThat(publisher.payloads()).containsExactly("{}", "{}");
        assertThat(store.isPublished(m.id())).isTrue();
    }

    @Test
    void dosRelaysSimultaneosNoPublicanLaMismaFila() throws Exception {
        seed("b-1", "{}", 0);
        CountDownLatch inFlight = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        publisher.beforeConfirm(p -> {
            if (first.getAndSet(false)) {
                inFlight.countDown();
                await(release);
            }
        });

        CompletableFuture<OutboxRelay.Result> firstRelay = CompletableFuture.supplyAsync(relay::publishPending);
        assertThat(inFlight.await(10, TimeUnit.SECONDS)).isTrue();

        OutboxRelay.Result secondResult = new OutboxRelay(store, publisher, transactionManager, clock, 100).publishPending();

        assertThat(secondResult).isEqualTo(new OutboxRelay.Result(0, 0, 0)); // la fila está bloqueada: SKIP LOCKED
        release.countDown();
        assertThat(firstRelay.get(10, TimeUnit.SECONDS)).isEqualTo(new OutboxRelay.Result(1, 0, 0));
        assertThat(publisher.published()).hasSize(1);
    }

    @Test
    void elTamanoDeLoteDebeSerPositivo() {
        assertThatThrownBy(() -> new OutboxRelay(store, publisher, transactionManager, clock, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Intentos y último error ---------------------------------------------------------------------------------

    @Test
    void cadaFalloSumaUnIntentoYGuardaElUltimoErrorConSuCausa() {
        OutboxMessage m = seed("b-1", "{}", 0);
        publisher.failFor(BATCH_TOPIC, "b-1");

        relay.publishPending();

        assertThat(store.attempts(m.id())).isEqualTo(1);
        assertThat(store.lastError(m.id()))
                .contains("EventPublishException")
                .contains("sin confirmación de Kafka para b-1")
                .contains("causa: RuntimeException: broker caído");
        assertThat(store.isPublished(m.id())).isFalse();

        relay.publishPending();
        relay.publishPending();

        assertThat(store.attempts(m.id())).isEqualTo(3);
    }

    @Test
    void alPublicarConExitoLosIntentosPreviosSeConservanComoHistorial() {
        OutboxMessage m = seed("b-1", "{}", 0);
        publisher.failFor(BATCH_TOPIC, "b-1");
        relay.publishPending();
        relay.publishPending();
        publisher.recover();

        relay.publishPending();

        assertThat(store.isPublished(m.id())).isTrue();
        assertThat(store.attempts(m.id())).isEqualTo(2);
    }

    @Test
    void unaPublicacionExitosaNoTocaIntentosNiError() {
        OutboxMessage m = seed("b-1", "{}", 0);

        relay.publishPending();

        assertThat(store.attempts(m.id())).isZero();
        assertThat(store.lastError(m.id())).isNull();
    }

    @Test
    void losMensajesOmitidosPorSeguirAUnoFallidoNoSumanIntentos() {
        OutboxMessage failing = seed("b-1", "a1", 0);
        OutboxMessage skipped = seed("b-1", "a2", 1);
        OutboxMessage other = seed("b-2", "b1", 2);
        publisher.failFor(BATCH_TOPIC, "b-1");

        relay.publishPending();

        assertThat(store.attempts(failing.id())).isEqualTo(1);
        assertThat(store.attempts(skipped.id())).isZero();
        assertThat(store.attempts(other.id())).isZero();
    }

    @Test
    void registraUnWarnConEventIdTopicoYError() {
        OutboxMessage m = seed("b-1", "{}", 0);
        publisher.failFor(BATCH_TOPIC, "b-1");

        relay.publishPending();

        List<ILoggingEvent> warnings = logs.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).getFormattedMessage())
                .contains(m.id().toString())
                .contains(BATCH_TOPIC)
                .contains("sin confirmación de Kafka para b-1")
                .contains("broker caído");
    }

    @Test
    void sinFallosNoHayWarn() {
        seed("b-1", "{}", 0);

        relay.publishPending();

        assertThat(logs.list).noneMatch(e -> e.getLevel() == Level.WARN);
    }

    @Test
    void siNoSePuedeRegistrarElIntentoElRestoDelLoteSigueYSeAvisa() {
        seed("b-1", "a1", 0);
        OutboxMessage other = seed("b-2", "b1", 1);
        publisher.failFor(BATCH_TOPIC, "b-1");
        store.failMarkFailedWith(new IllegalStateException("base lenta"));

        OutboxRelay.Result result = relay.publishPending();

        assertThat(result).isEqualTo(new OutboxRelay.Result(1, 1, 0));
        assertThat(store.isPublished(other.id())).isTrue();
        assertThat(transactionManager.outcomes()).containsExactly("commit");
        assertThat(logs.list).anyMatch(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains("base lenta"));
    }

    @Test
    void elErrorGuardadoSeAcotaAlMaximo() {
        String longMessage = "x".repeat(5000);

        String error = OutboxRelay.describe(new IllegalStateException(longMessage));

        assertThat(error).hasSize(OutboxRelay.MAX_ERROR_LENGTH).startsWith("IllegalStateException: xxx").endsWith("…");
    }

    @Test
    void sinCausaDistintaElErrorNoRepiteLaCausa() {
        assertThat(OutboxRelay.describe(new IllegalStateException("solo"))).isEqualTo("IllegalStateException: solo");
    }

    // --- Ayudas ---------------------------------------------------------------------------------

    private OutboxMessage seed(String aggregateId, String payload, int secondsAfterT0) {
        return seed(BATCH_TOPIC, aggregateId, payload, secondsAfterT0);
    }

    private OutboxMessage seed(String topic, String aggregateId, String payload, int secondsAfterT0) {
        OutboxMessage message = message(UUID.randomUUID(), topic, aggregateId, payload, T0.plusSeconds(secondsAfterT0));
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> store.save(message));
        transactionManager.outcomes().clear();
        return message;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

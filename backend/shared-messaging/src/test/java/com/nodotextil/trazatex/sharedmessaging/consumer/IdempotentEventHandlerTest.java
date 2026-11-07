package com.nodotextil.trazatex.sharedmessaging.consumer;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedkernel.testsupport.RecordingTransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotentEventHandlerTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private InMemoryProcessedEventStore processed;
    private RecordingTransactionManager transactionManager;
    private IdempotentEventHandler handler;
    private List<BaseDomainEvent> applied;

    @BeforeEach
    void setUp() {
        processed = new InMemoryProcessedEventStore();
        transactionManager = new RecordingTransactionManager();
        handler = new IdempotentEventHandler(processed, transactionManager, mapper);
        applied = new ArrayList<>();
    }

    @Test
    void aplicaElEfectoUnaVezYRegistraElEventIdEnLaMismaTransaccion() {
        BaseDomainEvent event = event("b-1");

        boolean first = handler.handle(event, applied::add);

        assertThat(first).isTrue();
        assertThat(applied).containsExactly(event);
        assertThat(processed.contains(event.eventId())).isTrue();
        assertThat(transactionManager.outcomes()).containsExactly("commit");
    }

    @Test
    void unEventoDuplicadoNoAplicaElEfectoDosVeces() {
        BaseDomainEvent event = event("b-1");

        handler.handle(event, applied::add);
        boolean second = handler.handle(event, applied::add);

        assertThat(second).isFalse();
        assertThat(applied).hasSize(1);
        assertThat(processed.size()).isEqualTo(1);
    }

    @Test
    void eventosDistintosSeAplicanCadaUno() {
        handler.handle(event("b-1"), applied::add);
        handler.handle(event("b-1"), applied::add);

        assertThat(applied).hasSize(2);
    }

    @Test
    void siElEfectoFallaSeRevierteElRegistroYElReintentoLoAplica() {
        BaseDomainEvent event = event("b-1");
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> handler.handle(event, e -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("fallo del caso de uso");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(processed.contains(event.eventId())).isFalse();
        assertThat(transactionManager.outcomes()).containsExactly("rollback");

        boolean retried = handler.handle(event, e -> attempts.incrementAndGet());

        assertThat(retried).isTrue();
        assertThat(attempts).hasValue(2);
        assertThat(processed.contains(event.eventId())).isTrue();
    }

    @Test
    void recibeElSobreEnJsonYLoDeserializa() throws Exception {
        BaseDomainEvent event = event("b-1");

        boolean handled = handler.handle(mapper.writeValueAsString(event), applied::add);

        assertThat(handled).isTrue();
        assertThat(applied.get(0).eventId()).isEqualTo(event.eventId());
        assertThat(applied.get(0).payload()).isEqualTo(Map.of("batchId", "b-1"));
    }

    @Test
    void elMismoJsonEntregadoDosVecesSeAplicaUnaSolaVez() throws Exception {
        String json = mapper.writeValueAsString(event("b-1"));

        handler.handle(json, applied::add);
        handler.handle(json, applied::add);

        assertThat(applied).hasSize(1);
    }

    @Test
    void unMensajeQueNoEsUnSobreValidoLanzaMalformedEventSinEfectoNiTransaccion() {
        for (String bad : new String[] {"no es json", "{}", "{\"eventId\":\"no-uuid\"}", "[]"}) {
            assertThatThrownBy(() -> handler.handle(bad, applied::add)).isInstanceOf(MalformedEventException.class);
        }

        assertThat(applied).isEmpty();
        assertThat(processed.size()).isZero();
        assertThat(transactionManager.outcomes()).isEmpty();
    }

    @Test
    void dosEntregasSimultaneasDelMismoEventoAplicanElEfectoUnaVez() throws Exception {
        BaseDomainEvent event = event("b-1");
        CountDownLatch inEffect = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger effects = new AtomicInteger();

        CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(() -> handler.handle(event, e -> {
            effects.incrementAndGet();
            inEffect.countDown();
            await(release);
        }));
        assertThat(inEffect.await(10, TimeUnit.SECONDS)).isTrue();
        Thread[] secondThread = new Thread[1];
        CompletableFuture<Boolean> second = new CompletableFuture<>();
        secondThread[0] = new Thread(() -> {
            try {
                second.complete(handler.handle(event, e -> effects.incrementAndGet()));
            } catch (Throwable t) {
                second.completeExceptionally(t);
            }
        });
        secondThread[0].setDaemon(true);
        secondThread[0].start();
        awaitBlocked(secondThread[0]);
        assertThat(second).isNotDone();

        release.countDown();

        assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
        assertThat(second.get(10, TimeUnit.SECONDS)).isFalse();
        assertThat(effects).hasValue(1);
    }

    private static BaseDomainEvent event(String aggregateId) {
        return BaseDomainEvent.of("BatchRegistered", aggregateId, Map.of("batchId", aggregateId));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitBlocked(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (thread.getState() != Thread.State.WAITING && thread.getState() != Thread.State.TIMED_WAITING) {
            assertThat(System.nanoTime()).isLessThan(deadline);
            Thread.sleep(5);
        }
    }
}

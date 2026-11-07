package com.nodotextil.trazatex.sharedkernel.outbox;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedkernel.testsupport.MutableClock;
import com.nodotextil.trazatex.sharedkernel.testsupport.RecordingTransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxEventRecorderTest {

    private static final String TOPIC = "production.batch";

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private InMemoryOutboxStore store;
    private RecordingEventPublisher publisher;
    private RecordingTransactionManager transactionManager;
    private MutableClock clock;
    private OutboxEventRecorder recorder;
    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        store = new InMemoryOutboxStore();
        publisher = new RecordingEventPublisher();
        transactionManager = new RecordingTransactionManager();
        clock = new MutableClock(Instant.parse("2026-10-19T14:00:00Z"));
        recorder = new OutboxEventRecorder(store, mapper, clock);
        transaction = new TransactionTemplate(transactionManager);
    }

    @Test
    void fueraDeUnaTransaccionNoEscribeElOutbox() {
        BaseDomainEvent event = BaseDomainEvent.of("BatchRegistered", "b-1", Map.of("batchId", "b-1"));

        assertThatThrownBy(() -> recorder.record("Batch", TOPIC, event)).isInstanceOf(IllegalStateException.class);
        assertThat(store.size()).isZero();
    }

    @Test
    void guardaElSobreCompletoComoJsonConElEventIdComoId() throws Exception {
        BaseDomainEvent event = BaseDomainEvent.of("BatchRegistered", "b-1", Map.of("batchId", "b-1"));

        transaction.executeWithoutResult(tx -> recorder.record("Batch", TOPIC, event));
        OutboxMessage saved = pending().get(0);

        assertThat(saved.id()).isEqualTo(event.eventId());
        assertThat(saved.aggregateType()).isEqualTo("Batch");
        assertThat(saved.aggregateId()).isEqualTo("b-1");
        assertThat(saved.eventType()).isEqualTo("BatchRegistered");
        assertThat(saved.topic()).isEqualTo(TOPIC);
        JsonNode envelope = mapper.readTree(saved.payload());
        assertThat(envelope.path("eventId").asString()).isEqualTo(event.eventId().toString());
        assertThat(envelope.path("type").asString()).isEqualTo("BatchRegistered");
        assertThat(envelope.path("payload").path("batchId").asString()).isEqualTo("b-1");
    }

    @Test
    void createdAtEsEstrictamenteCrecienteAunqueElRelojNoAvance() {
        transaction.executeWithoutResult(tx -> {
            for (int i = 0; i < 5; i++) {
                recorder.record("Batch", TOPIC, BaseDomainEvent.of("BatchPhaseChanged", "b-1", Map.of("n", i)));
            }
        });

        List<Instant> createdAt = pending().stream().map(OutboxMessage::createdAt).toList();

        assertThat(createdAt).isSorted().doesNotHaveDuplicates();
        assertThat(createdAt).allSatisfy(t -> assertThat(t).isEqualTo(t.truncatedTo(ChronoUnit.MICROS)));
    }

    @Test
    void siLaTransaccionDelCasoDeUsoSeRevierteNoQuedaEventoNiSePublicaNada() {
        BaseDomainEvent event = BaseDomainEvent.of("BatchRegistered", "b-1", Map.of("batchId", "b-1"));

        transaction.executeWithoutResult(tx -> {
            recorder.record("Batch", TOPIC, event);
            tx.setRollbackOnly();
        });
        new OutboxRelay(store, publisher, transactionManager, clock, 100).publishPending();

        assertThat(store.size()).isZero();
        assertThat(publisher.published()).isEmpty();
    }

    @Test
    void elEventoSePublicaSoloTrasElCommitYEnElOrdenDeRegistro() {
        OutboxRelay relay = new OutboxRelay(store, publisher, transactionManager, clock, 100);
        List<Integer> seenByRelayDuringTransaction = new ArrayList<>();

        transaction.executeWithoutResult(tx -> {
            recorder.record("Batch", TOPIC, BaseDomainEvent.of("BatchSplit", "b-1", Map.of("n", 1)));
            recorder.record("Batch", TOPIC, BaseDomainEvent.of("BatchPhaseChanged", "b-1", Map.of("n", 2)));
            seenByRelayDuringTransaction.add(publisher.published().size());
        });
        relay.publishPending();

        assertThat(seenByRelayDuringTransaction).containsExactly(0);
        assertThat(publisher.published()).extracting(RecordingEventPublisher.Published::key).containsExactly("b-1", "b-1");
        assertThat(publisher.payloads().get(0)).contains("BatchSplit");
        assertThat(publisher.payloads().get(1)).contains("BatchPhaseChanged");
    }

    private List<OutboxMessage> pending() {
        return transaction.execute(tx -> store.findPending(100));
    }
}

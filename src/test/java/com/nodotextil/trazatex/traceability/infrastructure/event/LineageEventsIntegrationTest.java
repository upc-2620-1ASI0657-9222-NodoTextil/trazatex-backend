package com.nodotextil.trazatex.traceability.infrastructure.event;

import com.nodotextil.trazatex.production.application.event.BatchSplitEvent;
import com.nodotextil.trazatex.production.application.event.TransferAcceptedEvent;
import com.nodotextil.trazatex.production.application.event.TransformationCompletedEvent;
import com.nodotextil.trazatex.traceability.application.RecordLineageUseCase;
import com.nodotextil.trazatex.traceability.domain.LineageEdge;
import com.nodotextil.trazatex.traceability.domain.LineageRepository;
import com.nodotextil.trazatex.traceability.domain.LineageType;
import com.nodotextil.trazatex.traceability.support.InMemoryLineageRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest(classes = {
        ProductionEventListener.class,
        LineageEventsIntegrationTest.TestConfig.class })
class LineageEventsIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 20, 14, 0);

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        InMemoryLineageRepository lineageRepository() {
            return new InMemoryLineageRepository();
        }

        @Bean
        RecordLineageUseCase recordLineageUseCase(LineageRepository lineageRepository) {
            return new RecordLineageUseCase(lineageRepository);
        }
    }

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private InMemoryLineageRepository repository;

    @BeforeEach
    void cleanGraph() {
        repository.clear();
    }

    @Test
    void aBatchSplitCreatesOneRelationPerChild() {
        UUID parent = UUID.randomUUID();
        UUID child1 = UUID.randomUUID();
        UUID child2 = UUID.randomUUID();

        publisher.publishEvent(new BatchSplitEvent(parent, List.of(child1, child2), NOW));

        assertThat(repository.edges())
                .extracting(LineageEdge::parentBatchId, LineageEdge::childBatchId, LineageEdge::type)
                .containsExactlyInAnyOrder(
                        tuple(parent, child1, LineageType.DIVISION),
                        tuple(parent, child2, LineageType.DIVISION));
    }

    @Test
    void receivingTheSameSplitTwiceDoesNotDuplicateRelations() {
        BatchSplitEvent event = new BatchSplitEvent(
                UUID.randomUUID(), List.of(UUID.randomUUID(), UUID.randomUUID()), NOW);

        publisher.publishEvent(event);
        publisher.publishEvent(event);

        assertThat(repository.edges()).hasSize(2);
    }

    @Test
    void aCompletedTransformationRelatesEveryInputWithEveryOutput() {
        publisher.publishEvent(new TransformationCompletedEvent(
                List.of(UUID.randomUUID(), UUID.randomUUID()),
                List.of(UUID.randomUUID(), UUID.randomUUID()),
                NOW));

        assertThat(repository.edges()).hasSize(4)
                .allSatisfy(edge -> assertThat(edge.type()).isEqualTo(LineageType.TRANSFORMATION));
    }

    @Test
    void aTransferDoesNotCreateAParentChildRelation() {
        publisher.publishEvent(new TransferAcceptedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW));

        assertThat(repository.edges()).isEmpty();
    }
}

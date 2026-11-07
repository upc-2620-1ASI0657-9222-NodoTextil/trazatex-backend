package com.nodotextil.trazatex.traceability.application.usecase;

import com.nodotextil.trazatex.traceability.application.dto.AnalyzeFailureImpactCommand;
import com.nodotextil.trazatex.traceability.application.port.out.ImpactEventPort;
import com.nodotextil.trazatex.traceability.domain.exception.ImpactAnalysisIncompleteException;
import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;
import com.nodotextil.trazatex.traceability.domain.model.impact.ImpactResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.nodotextil.trazatex.traceability.application.usecase.FakeLineageReadPort.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US33: qué hace {@code FailureConfirmed} con el resultado del análisis: lo registra una sola vez, nada si no hay afectados y nada si la familia está incompleta. */
@ExtendWith(OutputCaptureExtension.class)
class FailureImpactServiceTest {

    /** El outbox como un puerto en memoria: lo que se registró y por qué falla. */
    private static final class InMemoryEvents implements ImpactEventPort {
        final List<UUID> failures = new ArrayList<>();
        final List<ImpactResult> results = new ArrayList<>();
        final Set<UUID> recorded = new HashSet<>();

        @Override
        public boolean alreadyRecorded(UUID failureId) {
            return recorded.contains(failureId);
        }

        @Override
        public void record(UUID failureId, ImpactResult result) {
            recorded.add(failureId);
            failures.add(failureId);
            results.add(result);
        }
    }

    private final FakeLineageReadPort graph = new FakeLineageReadPort();
    private final InMemoryEvents events = new InMemoryEvents();
    private final FailureImpactService service = new FailureImpactService(graph, events);

    private AnalyzeFailureImpactCommand failure(UUID failureId, String batch) {
        return new AnalyzeFailureImpactCommand(UUID.randomUUID(), failureId, id(batch));
    }

    @Test
    void aFailureWithAffectedBatchesRecordsTheEventOnceWithTheFailureAndTheSourceBatch() {
        graph.batch("S", "X").split("S", "X");
        UUID failure = UUID.randomUUID();

        service.apply(failure(failure, "S"));

        assertThat(events.failures).containsExactly(failure);
        assertThat(events.results.getFirst().sourceBatchId()).isEqualTo(id("S"));
        assertThat(events.results.getFirst().affected()).singleElement().satisfies(a -> {
            assertThat(a.batchId()).isEqualTo(id("X"));
            assertThat(a.commonAncestorBatchId()).isEqualTo(id("S"));
        });
    }

    @Test
    void noAffectedBatchesMeansNothingIsPublished() {
        graph.batch("S", "P").split("S", "P").phase("P", BatchPhase.PROCESSED);

        service.apply(failure(UUID.randomUUID(), "S"));

        assertThat(events.results).isEmpty();
    }

    @Test
    void aSecondFailureConfirmedOfTheSameFailureIsNotPublishedAgainEvenWithAnotherEventId() {
        graph.batch("S", "X").split("S", "X");
        UUID failure = UUID.randomUUID();

        service.apply(failure(failure, "S"));
        service.apply(failure(failure, "S"));
        service.apply(failure(UUID.randomUUID(), "S"));

        assertThat(events.failures).as("una por falla: la repetida no, la de otra falla sí").hasSize(2).first().isEqualTo(failure);
    }

    @Test
    void anIncompleteFamilyRecordsNothingAndThrowsTheRetryableException() {
        graph.batch("S").provisional("ANC").split("ANC", "S");

        assertThatThrownBy(() -> service.apply(failure(UUID.randomUUID(), "S"))).isInstanceOf(ImpactAnalysisIncompleteException.class);

        assertThat(events.results).isEmpty();
    }

    @Test
    void aFamilyBeyondTenLevelsPublishesWhatFitsAndWarnsWithTheFailureId(CapturedOutput output) {
        graph.chain("n", 13);
        UUID failure = UUID.randomUUID();

        service.apply(failure(failure, "n0"));

        assertThat(events.results.getFirst().affected()).hasSize(10);
        assertThat(output.getAll()).contains("WARN").contains(failure.toString()).contains("más allá de 10 niveles");
    }

    @Test
    void aFamilyWithinTheLimitDoesNotWarn(CapturedOutput output) {
        graph.chain("n", 5);

        service.apply(failure(UUID.randomUUID(), "n0"));

        assertThat(output.getAll()).doesNotContain("más allá de 10 niveles");
    }
}

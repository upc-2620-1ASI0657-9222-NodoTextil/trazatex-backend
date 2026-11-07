package com.nodotextil.trazatex.traceability.infrastructure.messaging;

import com.nodotextil.trazatex.traceability.domain.exception.ImpactAnalysisIncompleteException;
import com.nodotextil.trazatex.traceability.integration.RedriveProcedure;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.listener.ListenerExecutionFailedException;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El aviso de un {@code FailureConfirmed} que agotó sus reintentos: dice que hay lotes sin retener, la falla, qué falta y cómo reenviarlo; y los comandos que
 * trae son <b>exactamente</b> los del procedimiento de {@code docs/progress.md} (el mismo que ejecuta {@code FailureImpactDeadLetterIT}).
 */
@ExtendWith(OutputCaptureExtension.class)
class ImpactDeadLetterReporterTest {

    private static final UUID FAILURE = UUID.fromString("f0000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID MISSING = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    private static ConsumerRecord<String, String> record() {
        return new ConsumerRecord<>("quality.failure", 0, 7L, FAILURE.toString(), "{}");
    }

    @Test
    void theErrorSaysThatThereAreAffectedBatchesWithoutHoldAndGivesTheFailureTheMissingNodesAndTheWayToResend(CapturedOutput output) {
        new ImpactDeadLetterReporter(11).accept(record(), new ListenerExecutionFailedException("falló", new ImpactAnalysisIncompleteException(SOURCE, Set.of(MISSING))));

        assertThat(output.getAll()).contains("ERROR").contains("HAY LOTES POSIBLEMENTE AFECTADOS SIN RETENER").contains(FAILURE.toString()).contains(SOURCE.toString())
                .contains(MISSING.toString()).contains("11 intentos").contains("quality.failure.DLT").contains("docs/progress.md")
                .contains(ImpactDeadLetterReporter.COPY_COMMAND).contains(ImpactDeadLetterReporter.RUN_COMMAND.replace("<failureId>", FAILURE.toString()))
                .doesNotContain("<failureId>");
    }

    @Test
    void theCommandsInTheErrorAreExactlyTheOnesOfTheProcedureInProgressMd() {
        assertThat(RedriveProcedure.commands()).as("el procedimiento de progress.md").containsExactly(ImpactDeadLetterReporter.COPY_COMMAND, ImpactDeadLetterReporter.RUN_COMMAND);
    }

    @Test
    void anyOtherFailureLeavesNoSuchError(CapturedOutput output) {
        new ImpactDeadLetterReporter(6).accept(record(), new IllegalStateException("otra cosa"));
        new ImpactDeadLetterReporter(6).accept(record(), new ListenerExecutionFailedException("falló", new RuntimeException("otra más")));

        assertThat(output.getAll()).doesNotContain("SIN RETENER");
    }

    @Test
    void theIncompleteFamilyIsFoundThroughAnyDepthOfWrappers() {
        ImpactAnalysisIncompleteException inner = new ImpactAnalysisIncompleteException(SOURCE, Set.of(MISSING));

        assertThat(ImpactDeadLetterReporter.causeOf(new RuntimeException(new IllegalStateException(new RuntimeException(inner))))).isSameAs(inner);
        assertThat(ImpactDeadLetterReporter.causeOf(inner)).isSameAs(inner);
        assertThat(ImpactDeadLetterReporter.causeOf(new RuntimeException("sin causa"))).isNull();
        assertThat(ImpactDeadLetterReporter.causeOf(null)).isNull();
    }
}

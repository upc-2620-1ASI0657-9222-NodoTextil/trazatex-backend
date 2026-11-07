package com.nodotextil.trazatex.traceability.infrastructure.messaging;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedkernel.testsupport.RecordingTransactionManager;
import com.nodotextil.trazatex.sharedmessaging.consumer.IdempotentEventHandler;
import com.nodotextil.trazatex.sharedmessaging.consumer.ProcessedEventPort;
import com.nodotextil.trazatex.traceability.application.dto.ApplyFailureCauseCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyQualityTestsCommand;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyQualityDetailsUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link QualityDetailsListener} traduce {@code QualityControlCompleted} (con pruebas) y {@code QualityFailureUpdated} a comandos del puerto de entrada
 * {@link ApplyQualityDetailsUseCase}, una sola vez por evento; lo demás de esos tópicos se ignora sin registrarse como procesado.
 */
class QualityDetailsListenerTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final Instant NOW = Instant.parse("2026-10-23T10:00:00Z");
    private static final UUID BATCH = UUID.fromString("00000000-0000-0000-0000-00000000ba01");

    private final Set<UUID> processed = new HashSet<>();
    private final ProcessedEventPort processedEvents = (eventId, type) -> processed.add(eventId);
    private final IdempotentEventHandler handler = new IdempotentEventHandler(processedEvents, new RecordingTransactionManager(), MAPPER);
    private final ApplyQualityDetailsUseCase details = mock(ApplyQualityDetailsUseCase.class);
    private final QualityDetailsListener listener = new QualityDetailsListener(handler, MAPPER, details);

    private static String message(UUID eventId, String type, Map<String, Object> payload) {
        return MAPPER.writeValueAsString(new BaseDomainEvent(eventId, type, eventId.toString(), 1, NOW, payload));
    }

    private static Map<String, Object> test(String criterion, String expected, String obtained, String unit, String result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("criterionName", criterion);
        map.put("expectedValue", expected);
        map.put("obtainedValue", obtained);
        map.put("unit", unit);
        map.put("testResult", result);
        return map;
    }

    @Test
    void aCompletedControlWithTestsBecomesTheTestsCommandOnce() {
        UUID eventId = UUID.randomUUID();
        String json = message(eventId, "QualityControlCompleted", Map.of("batchId", BATCH.toString(), "controlId", UUID.randomUUID().toString(),
                "tests", List.of(test("Resistencia", "28", "23", "cN/tex", "FAILED"), test("Humedad", "8", "7", "%", "CONFORMING"))));

        listener.tests(json);
        listener.tests(json);

        ArgumentCaptor<ApplyQualityTestsCommand> command = ArgumentCaptor.forClass(ApplyQualityTestsCommand.class);
        verify(details, times(1)).applyTests(command.capture());
        assertThat(command.getValue().eventId()).isEqualTo(eventId);
        assertThat(command.getValue().batchId()).isEqualTo(BATCH);
        assertThat(command.getValue().occurredAt()).isEqualTo(NOW);
        assertThat(command.getValue().tests()).containsExactly(new ApplyQualityTestsCommand.TestValue("Resistencia", "28", "23", "cN/tex", "FAILED"),
                new ApplyQualityTestsCommand.TestValue("Humedad", "8", "7", "%", "CONFORMING"));
    }

    @Test
    void aFailureUpdateBecomesTheFailureCauseCommandAndAMissingCauseIsEmpty() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        listener.failures(message(first, "QualityFailureUpdated", Map.of("batchId", BATCH.toString(), "cause", "Fibra corta", "status", "CONFIRMED")));
        listener.failures(message(second, "QualityFailureUpdated", Map.of("batchId", BATCH.toString(), "status", "OPEN")));

        ArgumentCaptor<ApplyFailureCauseCommand> command = ArgumentCaptor.forClass(ApplyFailureCauseCommand.class);
        verify(details, times(2)).applyFailureCause(command.capture());
        assertThat(command.getAllValues()).extracting(ApplyFailureCauseCommand::eventId).containsExactly(first, second);
        assertThat(command.getAllValues()).extracting(ApplyFailureCauseCommand::cause).containsExactly("Fibra corta", "");
        assertThat(command.getAllValues()).extracting(ApplyFailureCauseCommand::status).containsExactly("CONFIRMED", "OPEN");
        assertThat(command.getAllValues()).allSatisfy(c -> {
            assertThat(c.batchId()).isEqualTo(BATCH);
            assertThat(c.occurredAt()).isEqualTo(NOW);
        });
    }

    @Test
    void otherEventsOfThoseTopicsAndAControlWithoutTestsAreIgnoredAndNotRecordedAsProcessed() {
        listener.tests(message(UUID.randomUUID(), "QualityStatusChanged", Map.of("batchId", BATCH.toString(), "newStatus", "FAILED")));
        listener.tests(message(UUID.randomUUID(), "QualityControlCompleted", Map.of("batchId", BATCH.toString())));
        listener.failures(message(UUID.randomUUID(), "FailureConfirmed", Map.of("batchId", BATCH.toString(), "cause", "Fibra corta")));

        verifyNoInteractions(details);
        assertThat(processed).isEmpty();
    }
}

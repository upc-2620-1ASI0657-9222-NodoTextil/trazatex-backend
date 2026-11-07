package com.nodotextil.trazatex.traceability.infrastructure.messaging;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedkernel.testsupport.RecordingTransactionManager;
import com.nodotextil.trazatex.sharedmessaging.consumer.IdempotentEventHandler;
import com.nodotextil.trazatex.sharedmessaging.consumer.MalformedEventException;
import com.nodotextil.trazatex.sharedmessaging.consumer.ProcessedEventPort;
import com.nodotextil.trazatex.traceability.application.dto.AnalyzeFailureImpactCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyBatchPhaseChangedCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyBatchSplitCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyCompanyPrivacyCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyCompanyRegisteredCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyTransformationCompletedCommand;
import com.nodotextil.trazatex.traceability.application.dto.RegisterBatchCommand;
import com.nodotextil.trazatex.traceability.application.port.in.AnalyzeFailureImpactUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchPhaseChangedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyBatchSplitUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyPrivacyUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyRegisteredUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyQualityStatusUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransferAcceptedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyTransformationCompletedUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.RegisterBatchUseCase;
import com.nodotextil.trazatex.traceability.domain.exception.ImpactAnalysisIncompleteException;
import com.nodotextil.trazatex.traceability.domain.model.BatchPhase;
import com.nodotextil.trazatex.traceability.integration.EventMessages;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Los listeners traducen el mensaje del catálogo a la llamada del caso de uso; lo ajeno se ignora y lo ilegible es {@link MalformedEventException}. */
class EventListenersTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

    private final Set<UUID> processed = new HashSet<>();
    /** Como el real: el registro se deshace si la transacción se revierte. */
    private final ProcessedEventPort processedEvents = (eventId, type) -> {
        boolean added = processed.add(eventId);
        if (added) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        processed.remove(eventId);
                    }
                }
            });
        }
        return added;
    };
    private final IdempotentEventHandler handler = new IdempotentEventHandler(processedEvents, new RecordingTransactionManager(), MAPPER);
    private final TransientRetry retry = new TransientRetry(1, Duration.ZERO);

    private final RegisterBatchUseCase register = mock(RegisterBatchUseCase.class);
    private final ApplyBatchSplitUseCase split = mock(ApplyBatchSplitUseCase.class);
    private final ApplyBatchPhaseChangedUseCase phase = mock(ApplyBatchPhaseChangedUseCase.class);
    private final ApplyTransformationCompletedUseCase transformation = mock(ApplyTransformationCompletedUseCase.class);
    private final ApplyTransferAcceptedUseCase transfer = mock(ApplyTransferAcceptedUseCase.class);
    private final ApplyQualityStatusUseCase quality = mock(ApplyQualityStatusUseCase.class);
    private final ApplyCompanyPrivacyUseCase privacy = mock(ApplyCompanyPrivacyUseCase.class);
    private final ApplyCompanyRegisteredUseCase registered = mock(ApplyCompanyRegisteredUseCase.class);
    private final AnalyzeFailureImpactUseCase impact = mock(AnalyzeFailureImpactUseCase.class);

    private final BatchEventsListener batches = new BatchEventsListener(handler, retry, MAPPER, register, split, phase);
    private final TransformationEventsListener transformations = new TransformationEventsListener(handler, retry, MAPPER, transformation);
    private final TransferEventsListener transfers = new TransferEventsListener(handler, retry, MAPPER, transfer);
    private final QualityEventsListener qualities = new QualityEventsListener(handler, retry, MAPPER, quality);
    private final CompanyEventsListener companies = new CompanyEventsListener(handler, retry, MAPPER, privacy, registered);
    private final QualityFailureListener failures = new QualityFailureListener(handler, retry, MAPPER, impact);

    private static ConsumerRecord<String, String> record(String topic, String type, Map<String, Object> payload) {
        UUID eventId = UUID.randomUUID();
        return new ConsumerRecord<>(topic, 0, 0, eventId.toString(), MAPPER.writeValueAsString(new BaseDomainEvent(eventId, type, eventId.toString(), 1, NOW, payload)));
    }

    private static Map<String, Object> map(Object... keysAndValues) {
        return EventMessages.map(keysAndValues);
    }

    // ---- lo ajeno se ignora ----

    @Test
    void everyListenerIgnoresTheTypesThatAreNotItsOwnWithoutRecordingThemAsProcessed() {
        batches.onMessage(record("production.batch", "UnTipoNuevo", map("x", 1)));
        batches.onMessage(record("production.batch", "TransferAccepted", map("x", 1)));
        transformations.onMessage(record("production.transformation", "BatchRegistered", map("x", 1)));
        transfers.onMessage(record("production.transfer", "TransferInitiated", map("x", 1)));
        transfers.onMessage(record("production.transfer", "TransferRejected", map("x", 1)));
        qualities.onMessage(record("quality.control", "QualityControlCompleted", map("x", 1)));
        companies.onMessage(record("orgaccess.company", "CompanyStatusChanged", map("x", 1)));

        assertThat(processed).isEmpty();
        verifyNoInteractions(register, split, phase, transformation, transfer, quality, privacy, registered);
    }

    // ---- del catálogo a la llamada ----

    @Test
    void aPhaseChangeCarriesTheVersionTheNewPhaseTheHoldAndTheOwnerAndIgnoresTheRest() {
        UUID batch = UUID.randomUUID();
        UUID company = UUID.randomUUID();

        batches.onMessage(record("production.batch", "BatchPhaseChanged", map("batchId", batch, "companyId", company, "previousPhase", "AVAILABLE",
                "newPhase", "IN_TRANSFER", "qualityHold", true, "reason", "X", "requestId", "r", "batchVersion", 9, "campoNuevo", "ignorado")));

        ArgumentCaptor<ApplyBatchPhaseChangedCommand> command = ArgumentCaptor.forClass(ApplyBatchPhaseChangedCommand.class);
        verify(phase).apply(command.capture());
        assertThat(command.getValue()).satisfies(c -> {
            assertThat(c.batchId()).isEqualTo(batch);
            assertThat(c.companyId()).isEqualTo(company);
            assertThat(c.newPhase()).isEqualTo(BatchPhase.IN_TRANSFER);
            assertThat(c.qualityHold()).isTrue();
            assertThat(c.batchVersion()).isEqualTo(9L);
        });
        assertThat(processed).hasSize(1);
    }

    @Test
    void aSplitCarriesEveryChildWithItsInheritedDataAndAFreshComposition() {
        UUID parent = UUID.randomUUID();
        UUID child = UUID.randomUUID();

        batches.onMessage(record("production.batch", "BatchSplit", map("parentBatchId", parent, "companyId", UUID.randomUUID(), "children",
                List.of(EventMessages.splitChild(child, "TT-1", "QR-1", "Algodón", 4, "Piura", "Textiles", "Seco", "CONFORMING", NOW)))));

        ArgumentCaptor<ApplyBatchSplitCommand> command = ArgumentCaptor.forClass(ApplyBatchSplitCommand.class);
        verify(split).apply(command.capture());
        assertThat(command.getValue().parentBatchId()).isEqualTo(parent);
        assertThat(command.getValue().children()).singleElement().satisfies(c -> {
            assertThat(c.batchId()).isEqualTo(child);
            assertThat(c.materialType()).isEqualTo("Algodón");
            assertThat(c.quantityKg()).isEqualByComparingTo("4");
            assertThat(c.composition()).singleElement().satisfies(part -> assertThat(part.name()).isEqualTo("Algodón"));
            assertThat(c.origin()).isEqualTo("Piura");
            assertThat(c.qualityStatus().name()).isEqualTo("CONFORMING");
            assertThat(c.createdAt()).isEqualTo(NOW);
        });
    }

    @Test
    void aTransformationWithoutMachineOrWasteReasonStillPassesAndKeepsThemNull() {
        UUID out = UUID.randomUUID();

        transformations.onMessage(record("production.transformation", "TransformationCompleted", map("transformationId", UUID.randomUUID(), "type", "SPINNING",
                "companyId", UUID.randomUUID(), "inputs", List.of(map("batchId", UUID.randomUUID(), "quantityKg", 1)), "outputs", List.of(map("batchId", out,
                        "traceabilityId", "TT-O", "qrCode", "QR-O", "materialType", "Hilo", "quantityKg", 9, "composition", EventMessages.cotton(), "finalProduct", false)),
                "wasteKg", 0, "shrinkageKg", 0, "startedAt", NOW, "completedAt", NOW)));

        ArgumentCaptor<ApplyTransformationCompletedCommand> command = ArgumentCaptor.forClass(ApplyTransformationCompletedCommand.class);
        verify(transformation).apply(command.capture());
        assertThat(command.getValue().machineName()).isNull();
        assertThat(command.getValue().wasteReason()).isNull();
        assertThat(command.getValue().outputs()).singleElement().satisfies(o -> assertThat(o.batchId()).isEqualTo(out));
    }

    @Test
    void thePrivacyMapTravelsAsItArrivedForTheUseCaseToInterpretWithTolerance() {
        companies.onMessage(record("orgaccess.company", "CompanyPrivacyUpdated", map("companyId", UUID.randomUUID(),
                "fields", map("SUPPLIER", "SHARED", "CAMPO_QUE_NO_EXISTE", "SHARED"))));

        ArgumentCaptor<ApplyCompanyPrivacyCommand> command = ArgumentCaptor.forClass(ApplyCompanyPrivacyCommand.class);
        verify(privacy).apply(command.capture());
        assertThat(command.getValue().fields()).containsEntry("SUPPLIER", "SHARED").containsEntry("CAMPO_QUE_NO_EXISTE", "SHARED");
    }

    @Test
    void aCompanyRegisteredCarriesOnlyTheCompanyAndItsNameAndIgnoresTheRestOfThePayload() {
        UUID company = UUID.randomUUID();

        companies.onMessage(record("orgaccess.company", "CompanyRegistered", map("companyId", company, "ruc", "20131312955", "businessName", "  Hilandería Andina SAC ",
                "activities", List.of("SPINNING"), "status", "ACTIVE")));

        ArgumentCaptor<ApplyCompanyRegisteredCommand> command = ArgumentCaptor.forClass(ApplyCompanyRegisteredCommand.class);
        verify(registered).apply(command.capture());
        assertThat(command.getValue().companyId()).isEqualTo(company);
        assertThat(command.getValue().businessName()).isEqualTo("Hilandería Andina SAC");
        assertThat(processed).hasSize(1);
    }

    @Test
    void aCompanyRegisteredWithoutItsNameOrItsIdIsMalformed() {
        assertThatThrownBy(() -> companies.onMessage(record("orgaccess.company", "CompanyRegistered", map("companyId", UUID.randomUUID(), "status", "ACTIVE"))))
                .isInstanceOf(MalformedEventException.class).hasMessageContaining("businessName");
        assertThatThrownBy(() -> companies.onMessage(record("orgaccess.company", "CompanyRegistered", map("businessName", "Hilos SAC"))))
                .isInstanceOf(MalformedEventException.class).hasMessageContaining("companyId");
        verify(registered, never()).apply(any());
        assertThat(processed).isEmpty();
    }

    // ---- lo ilegible ----

    @Test
    void aMissingMandatoryFieldIsMalformedAndNothingIsRecorded() {
        ConsumerRecord<String, String> withoutVersion = record("production.batch", "BatchPhaseChanged",
                map("batchId", UUID.randomUUID(), "companyId", UUID.randomUUID(), "newPhase", "AVAILABLE", "qualityHold", false));
        ConsumerRecord<String, String> withoutHold = record("production.batch", "BatchPhaseChanged",
                map("batchId", UUID.randomUUID(), "companyId", UUID.randomUUID(), "newPhase", "AVAILABLE", "batchVersion", 1));
        ConsumerRecord<String, String> withoutQr = record("production.batch", "BatchRegistered",
                map("batchId", UUID.randomUUID(), "traceabilityId", "TT", "companyId", UUID.randomUUID(), "materialType", "A", "composition", EventMessages.cotton(),
                        "quantityKg", 1, "createdAt", NOW));

        assertThatThrownBy(() -> batches.onMessage(withoutVersion)).isInstanceOf(MalformedEventException.class).hasMessageContaining("batchVersion");
        assertThatThrownBy(() -> batches.onMessage(withoutHold)).isInstanceOf(MalformedEventException.class).hasMessageContaining("qualityHold");
        assertThatThrownBy(() -> batches.onMessage(withoutQr)).isInstanceOf(MalformedEventException.class).hasMessageContaining("qrCode");
        verify(phase, never()).apply(any());
        verify(register, never()).apply(any());
    }

    @Test
    void anUnknownEnumValueANonJsonBodyAndABadCompositionAreMalformed() {
        assertThatThrownBy(() -> batches.onMessage(record("production.batch", "BatchPhaseChanged", map("batchId", UUID.randomUUID(), "companyId",
                UUID.randomUUID(), "newPhase", "FLOTANDO", "qualityHold", false, "batchVersion", 1)))).isInstanceOf(MalformedEventException.class);
        assertThatThrownBy(() -> batches.onMessage(new ConsumerRecord<>("production.batch", 0, 0, "k", "esto no es JSON"))).isInstanceOf(MalformedEventException.class);
        assertThatThrownBy(() -> batches.onMessage(record("production.batch", "BatchRegistered", map("batchId", UUID.randomUUID(), "traceabilityId", "TT",
                "qrCode", "QR", "companyId", UUID.randomUUID(), "materialType", "A", "composition", List.of(map("name", "Algodón")), "quantityKg", 1,
                "createdAt", NOW)))).isInstanceOf(MalformedEventException.class).hasMessageContaining("composition");
        assertThatThrownBy(() -> qualities.onMessage(record("quality.control", "QualityStatusChanged", map("batchId", UUID.randomUUID(), "newStatus", "RARO"))))
                .isInstanceOf(MalformedEventException.class);
    }

    @Test
    void aTransientFailureOfTheUseCaseIsRepeatedByTheListener() {
        TransientRetry twice = new TransientRetry(2, Duration.ZERO);
        BatchEventsListener retrying = new BatchEventsListener(handler, twice, MAPPER, register, split, phase);
        int[] calls = {0};
        org.mockito.Mockito.doAnswer(call -> {
            if (calls[0]++ == 0) {
                throw new org.neo4j.driver.exceptions.TransientException("Neo.TransientError.Transaction.DeadlockDetected", "interbloqueo");
            }
            return null;
        }).when(phase).apply(any());

        retrying.onMessage(record("production.batch", "BatchPhaseChanged", map("batchId", UUID.randomUUID(), "companyId", UUID.randomUUID(),
                "newPhase", "AVAILABLE", "qualityHold", false, "batchVersion", 1)));

        assertThat(calls[0]).isEqualTo(2);
    }

    // ---- FailureConfirmed (US33) ----

    @Test
    void aFailureConfirmedCarriesOnlyTheFailureAndTheBatchAndIgnoresTheRestOfThePayload() {
        UUID failure = UUID.randomUUID();
        UUID batch = UUID.randomUUID();

        failures.onMessage(record("quality.failure", "FailureConfirmed", map("failureId", failure, "batchId", batch, "controlId", UUID.randomUUID(), "companyId", UUID.randomUUID(),
                "cause", "Hilado irregular", "affectedQuantityKg", 12.5, "confirmedAt", NOW, "campoDelFuturo", true)));

        ArgumentCaptor<AnalyzeFailureImpactCommand> command = ArgumentCaptor.forClass(AnalyzeFailureImpactCommand.class);
        verify(impact).apply(command.capture());
        assertThat(command.getValue().failureId()).isEqualTo(failure);
        assertThat(command.getValue().sourceBatchId()).isEqualTo(batch);
        assertThat(processed).hasSize(1);
    }

    @Test
    void aFailureConfirmedWithoutItsFailureOrItsBatchIsMalformedAndGoesStraightToTheDeadLetterTopic() {
        assertThatThrownBy(() -> failures.onMessage(record("quality.failure", "FailureConfirmed", map("batchId", UUID.randomUUID()))))
                .isInstanceOf(MalformedEventException.class).hasMessageContaining("failureId");
        assertThatThrownBy(() -> failures.onMessage(record("quality.failure", "FailureConfirmed", map("failureId", UUID.randomUUID()))))
                .isInstanceOf(MalformedEventException.class).hasMessageContaining("batchId");
        assertThatThrownBy(() -> failures.onMessage(record("quality.failure", "FailureConfirmed", map("failureId", "no-es-uuid", "batchId", UUID.randomUUID()))))
                .isInstanceOf(MalformedEventException.class);
        verify(impact, never()).apply(any());
        assertThat(processed).isEmpty();
    }

    @Test
    void anIncompleteFamilyIsNotMalformedSoTheContainerRetriesItAndTheEventLeavesNoProcessedRecord() {
        UUID batch = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ImpactAnalysisIncompleteException(batch, Set.of(UUID.randomUUID()))).when(impact).apply(any());

        assertThatThrownBy(() -> failures.onMessage(record("quality.failure", "FailureConfirmed", map("failureId", UUID.randomUUID(), "batchId", batch))))
                .isInstanceOf(ImpactAnalysisIncompleteException.class).isNotInstanceOf(MalformedEventException.class);
        assertThat(processed).as("al revertirse la transacción el evento no queda como procesado y el reintento lo vuelve a aplicar").isEmpty();
    }

    @Test
    void otherTypesOfTheFailureTopicAreIgnoredWithoutAProcessedRecord() {
        failures.onMessage(record("quality.failure", "FailureResolved", map("failureId", UUID.randomUUID(), "batchId", UUID.randomUUID())));

        verifyNoInteractions(impact);
        assertThat(processed).isEmpty();
    }
}

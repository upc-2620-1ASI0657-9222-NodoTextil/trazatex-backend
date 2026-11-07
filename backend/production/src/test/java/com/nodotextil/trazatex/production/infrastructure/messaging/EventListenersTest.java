package com.nodotextil.trazatex.production.infrastructure.messaging;

import com.nodotextil.trazatex.production.application.port.in.ApplyQualityStatusUseCase;
import com.nodotextil.trazatex.production.application.port.in.SyncCompanyProjectionUseCase;
import com.nodotextil.trazatex.production.domain.model.CompanyStatus;
import com.nodotextil.trazatex.production.domain.model.QualityStatus;
import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import com.nodotextil.trazatex.sharedkernel.testsupport.RecordingTransactionManager;
import com.nodotextil.trazatex.sharedmessaging.consumer.IdempotentEventHandler;
import com.nodotextil.trazatex.sharedmessaging.consumer.MalformedEventException;
import com.nodotextil.trazatex.sharedmessaging.consumer.NonRetryableEventException;
import com.nodotextil.trazatex.sharedmessaging.consumer.ProcessedEventPort;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** El rollback del registro en processed_events lo comprueba EventConsumptionKafkaIT con la base real (aquí el registro es un Set). */
class EventListenersTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final Set<UUID> processed = new HashSet<>();
    private final ProcessedEventPort processedEvents = (eventId, type) -> processed.add(eventId);
    private final IdempotentEventHandler handler = new IdempotentEventHandler(processedEvents, new RecordingTransactionManager(), mapper);
    private final SyncCompanyProjectionUseCase projection = mock(SyncCompanyProjectionUseCase.class);
    private final ApplyQualityStatusUseCase qualityStatus = mock(ApplyQualityStatusUseCase.class);
    private final CompanyEventsListener companies = new CompanyEventsListener(handler, projection, mapper);
    private final QualityEventsListener quality = new QualityEventsListener(handler, qualityStatus, mapper);
    private final UUID company = UUID.randomUUID();
    private final UUID batch = UUID.randomUUID();

    private BaseDomainEvent event(String type, Map<String, Object> payload) {
        return BaseDomainEvent.of(type, "agg", payload);
    }

    private ConsumerRecord<String, String> record(String topic, Object value) {
        return new ConsumerRecord<>(topic, 0, 0L, "k", value instanceof String text ? text : mapper.writeValueAsString(value));
    }

    private ConsumerRecord<String, String> companyRecord(BaseDomainEvent event) {
        return record(ConsumedTopics.ORGACCESS_COMPANY, event);
    }

    // ---- empresas ----

    @Test
    void companyRegisteredAppliesTheCatalogFieldsAndIgnoresTheRest() {
        companies.onMessage(companyRecord(event("CompanyRegistered", Map.of("companyId", company, "ruc", "20100070970",
                "businessName", "Hilos SAC", "activities", List.of("SPINNING"), "status", "ACTIVE"))));

        verify(projection).registered(company, "Hilos SAC", CompanyStatus.ACTIVE);
    }

    @Test
    void companyStatusChangedAppliesTheStatus() {
        companies.onMessage(companyRecord(event("CompanyStatusChanged", Map.of("companyId", company, "status", "INACTIVE"))));

        verify(projection).statusChanged(company, CompanyStatus.INACTIVE);
    }

    @Test
    void theSameEventDeliveredTwiceIsAppliedOnce() {
        ConsumerRecord<String, String> record = companyRecord(event("CompanyStatusChanged", Map.of("companyId", company, "status", "INACTIVE")));

        companies.onMessage(record);
        companies.onMessage(record);

        verify(projection, times(1)).statusChanged(company, CompanyStatus.INACTIVE);
    }

    @Test
    void anEventOfAnotherServiceOrAnUnknownTypeIsIgnoredWithoutErrorAndLeavesNoTrace() {
        BaseDomainEvent privacy = event("CompanyPrivacyUpdated", Map.of("companyId", company, "fields", Map.of("SUPPLIER", "SHARED")));
        BaseDomainEvent unknown = event("AlgoNuevo", Map.of("x", 1));

        companies.onMessage(companyRecord(privacy));
        companies.onMessage(companyRecord(unknown));

        verifyNoInteractions(projection);
        assertThat(processed).as("no se registra en processed_events").isEmpty();
    }

    @Test
    void anUnreadableEnvelopeOrPayloadIsMalformedAndGoesToTheDeadLetterTopic() {
        assertThatThrownBy(() -> companies.onMessage(record(ConsumedTopics.ORGACCESS_COMPANY, "esto no es un sobre")))
                .isInstanceOf(MalformedEventException.class);
        assertThatThrownBy(() -> companies.onMessage(companyRecord(event("CompanyStatusChanged", Map.of("companyId", company, "status", "SUSPENDED")))))
                .isInstanceOf(MalformedEventException.class);
        assertThatThrownBy(() -> companies.onMessage(companyRecord(event("CompanyStatusChanged", Map.of("status", "ACTIVE")))))
                .isInstanceOf(MalformedEventException.class);
        assertThatThrownBy(() -> companies.onMessage(companyRecord(event("CompanyRegistered", Map.of("companyId", company)))))
                .isInstanceOf(MalformedEventException.class);

        verifyNoInteractions(projection);
    }

    // ---- calidad ----

    private ConsumerRecord<String, String> qualityRecord(BaseDomainEvent event) {
        return record(ConsumedTopics.QUALITY_CONTROL, event);
    }

    @Test
    void qualityStatusChangedAppliesTheNewStatusByBatch() {
        UUID control = UUID.randomUUID();

        quality.onMessage(qualityRecord(event("QualityStatusChanged", Map.of("batchId", batch, "companyId", company,
                "previousStatus", "NOT_REVIEWED", "newStatus", "FAILED", "controlId", control))));

        verify(qualityStatus).apply(batch, company, QualityStatus.FAILED, control);
    }

    @Test
    void otherEventsOfTheQualityTopicAreIgnored() {
        quality.onMessage(qualityRecord(event("QualityControlCompleted", Map.of("controlId", UUID.randomUUID(), "batchId", batch))));

        verifyNoInteractions(qualityStatus);
        assertThat(processed).isEmpty();
    }

    @Test
    void aMissingBatchIsNotRetryable() {
        doThrow(new NotFoundException("BATCH_NOT_FOUND", "no existe")).when(qualityStatus).apply(any(), any(), any(), any());

        assertThatThrownBy(() -> quality.onMessage(qualityRecord(event("QualityStatusChanged", Map.of("batchId", batch,
                "companyId", company, "newStatus", "FAILED", "controlId", UUID.randomUUID())))))
                .isInstanceOf(NonRetryableEventException.class).hasCauseInstanceOf(NotFoundException.class);
    }

    @Test
    void aTransientFailureIsPropagatedAsIsSoTheConsumerRetriesIt() {
        doThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(Object.class, "lote")).when(qualityStatus)
                .apply(any(), any(), any(), any());

        assertThatThrownBy(() -> quality.onMessage(qualityRecord(event("QualityStatusChanged", Map.of("batchId", batch,
                "companyId", company, "newStatus", "FAILED", "controlId", UUID.randomUUID())))))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
                .isNotInstanceOf(NonRetryableEventException.class);
    }

    @Test
    void anUnknownQualityStatusIsMalformed() {
        assertThatThrownBy(() -> quality.onMessage(qualityRecord(event("QualityStatusChanged", Map.of("batchId", batch,
                "companyId", company, "newStatus", "EXCELENTE", "controlId", UUID.randomUUID())))))
                .isInstanceOf(MalformedEventException.class);
        verify(qualityStatus, never()).apply(any(), any(), any(), any());
    }
}

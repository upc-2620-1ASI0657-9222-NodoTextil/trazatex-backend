package com.nodotextil.trazatex.production.infrastructure.messaging;

import com.nodotextil.trazatex.production.application.port.in.SyncCompanyProjectionUseCase;
import com.nodotextil.trazatex.production.domain.model.CompanyStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedmessaging.consumer.EventEnvelopes;
import com.nodotextil.trazatex.sharedmessaging.consumer.IdempotentEventHandler;
import com.nodotextil.trazatex.sharedmessaging.consumer.MalformedEventException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * {@code orgaccess.company}: alimenta la proyección de empresas. Por el mismo tópico llegan eventos que no son de production
 * (p. ej. {@code CompanyPrivacyUpdated}, de traceability): se ignoran sin error y sin enviarlos a la DLT, y como se filtran
 * <b>antes</b> del consumo idempotente tampoco dejan filas en {@code processed_events}. Lo ilegible sí va a la DLT sin reintentos.
 */
@Component
@ConditionalOnProperty(name = "trazatex.messaging.enabled", havingValue = "true", matchIfMissing = true)
class CompanyEventsListener {

    private static final Logger log = LoggerFactory.getLogger(CompanyEventsListener.class);

    static final String COMPANY_REGISTERED = "CompanyRegistered";
    static final String COMPANY_STATUS_CHANGED = "CompanyStatusChanged";

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CompanyRegisteredPayload(UUID companyId, String businessName, CompanyStatus status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CompanyStatusChangedPayload(UUID companyId, CompanyStatus status) {
    }

    private final IdempotentEventHandler handler;
    private final SyncCompanyProjectionUseCase projection;
    private final ObjectMapper objectMapper;

    CompanyEventsListener(IdempotentEventHandler handler, SyncCompanyProjectionUseCase projection, ObjectMapper objectMapper) {
        this.handler = handler;
        this.projection = projection;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(id = "production-orgaccess-company", topics = ConsumedTopics.ORGACCESS_COMPANY,
            groupId = "${trazatex.messaging.consumer.company.group:production.orgaccess-company}")
    void onMessage(ConsumerRecord<String, String> record) {
        BaseDomainEvent event = EventEnvelopes.parse(objectMapper, record.value());
        switch (event.type()) {
            case COMPANY_REGISTERED -> handler.handle(event, e -> {
                CompanyRegisteredPayload payload = EventEnvelopes.payload(objectMapper, e, CompanyRegisteredPayload.class);
                require(e, payload.companyId() != null && payload.status() != null, "companyId y status");
                projection.registered(payload.companyId(), payload.businessName(), payload.status());
            });
            case COMPANY_STATUS_CHANGED -> handler.handle(event, e -> {
                CompanyStatusChangedPayload payload = EventEnvelopes.payload(objectMapper, e, CompanyStatusChangedPayload.class);
                require(e, payload.companyId() != null && payload.status() != null, "companyId y status");
                projection.statusChanged(payload.companyId(), payload.status());
            });
            default -> log.debug("Evento {} ({}) de {} no es de production: se ignora", event.eventId(), event.type(), record.topic());
        }
    }

    static void require(BaseDomainEvent event, boolean valid, String fields) {
        if (!valid) {
            throw new MalformedEventException("El payload de " + event.type() + " (" + event.eventId() + ") debe traer " + fields, null);
        }
    }
}

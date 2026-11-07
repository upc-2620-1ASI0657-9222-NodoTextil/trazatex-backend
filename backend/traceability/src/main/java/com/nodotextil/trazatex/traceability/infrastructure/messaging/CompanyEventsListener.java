package com.nodotextil.trazatex.traceability.infrastructure.messaging;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedmessaging.consumer.IdempotentEventHandler;
import com.nodotextil.trazatex.traceability.application.dto.ApplyCompanyPrivacyCommand;
import com.nodotextil.trazatex.traceability.application.dto.ApplyCompanyRegisteredCommand;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyPrivacyUseCase;
import com.nodotextil.trazatex.traceability.application.port.in.ApplyCompanyRegisteredUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.function.Consumer;

/**
 * {@code orgaccess.company}: {@code CompanyPrivacyUpdated} y {@code CompanyRegistered} (solo para guardar el nombre de la empresa). Por el
 * mismo tópico llega {@code CompanyStatusChanged}, que es de production y se ignora. El tópico no se borra por tiempo ({@code retention.ms=-1}):
 * un traceability nuevo conoce la privacidad de todas las empresas leyéndolo desde el inicio.
 */
@Component
@ConditionalOnProperty(name = "trazatex.messaging.enabled", havingValue = "true", matchIfMissing = true)
class CompanyEventsListener extends AbstractEventListener {

    private final Map<String, Consumer<BaseDomainEvent>> effects;

    CompanyEventsListener(IdempotentEventHandler handler, TransientRetry retry, ObjectMapper objectMapper, ApplyCompanyPrivacyUseCase privacy,
                          ApplyCompanyRegisteredUseCase registered) {
        super(handler, retry, objectMapper);
        this.effects = Map.of(
                "CompanyPrivacyUpdated", e -> {
                    EventPayloads.CompanyPrivacyUpdated p = payload(e, EventPayloads.CompanyPrivacyUpdated.class);
                    privacy.apply(new ApplyCompanyPrivacyCommand(e.eventId(), p.companyId(), p.fields()));
                },
                "CompanyRegistered", e -> {
                    EventPayloads.CompanyRegistered p = payload(e, EventPayloads.CompanyRegistered.class);
                    registered.apply(new ApplyCompanyRegisteredCommand(e.eventId(), p.companyId(), p.businessName()));
                });
    }

    @Override
    Map<String, Consumer<BaseDomainEvent>> effects() {
        return effects;
    }

    @KafkaListener(id = ConsumedTopics.COMPANY_LISTENER_ID, topics = ConsumedTopics.ORGACCESS_COMPANY, autoStartup = "false",
            groupId = "${trazatex.messaging.consumer.company.group:traceability.orgaccess-company}")
    void onMessage(ConsumerRecord<String, String> record) {
        consume(record);
    }
}

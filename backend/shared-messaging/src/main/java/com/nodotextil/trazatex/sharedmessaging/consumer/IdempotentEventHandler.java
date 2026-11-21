package com.nodotextil.trazatex.sharedmessaging.consumer;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.function.Consumer;

public class IdempotentEventHandler {

    private static final Logger log = LoggerFactory.getLogger(IdempotentEventHandler.class);

    private final ProcessedEventPort processedEvents;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public IdempotentEventHandler(ProcessedEventPort processedEvents, PlatformTransactionManager transactionManager,
                                  ObjectMapper objectMapper) {
        this.processedEvents = processedEvents;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
    }

    public boolean handle(String envelopeJson, Consumer<BaseDomainEvent> effect) {
        return handle(parse(envelopeJson), effect);
    }

    public boolean handle(BaseDomainEvent event, Consumer<BaseDomainEvent> effect) {
        return Boolean.TRUE.equals(transactionTemplate.execute(tx -> {
            if (!processedEvents.markProcessed(event.eventId(), event.type())) {
                log.debug("Evento {} ({}) ya procesado; se descarta", event.eventId(), event.type());
                return false;
            }
            effect.accept(event);
            return true;
        }));
    }

    private BaseDomainEvent parse(String envelopeJson) {
        return EventEnvelopes.parse(objectMapper, envelopeJson);
    }
}

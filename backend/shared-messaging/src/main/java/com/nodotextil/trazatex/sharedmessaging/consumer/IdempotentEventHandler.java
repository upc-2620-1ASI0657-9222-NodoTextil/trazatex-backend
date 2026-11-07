package com.nodotextil.trazatex.sharedmessaging.consumer;

import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.function.Consumer;

/**
 * Consumo idempotente (ADR-004): registra el {@code eventId} y aplica el efecto <b>en la misma transacción</b>.
 * Un evento duplicado se descarta sin ejecutar el efecto; si el efecto falla, la transacción se revierte junto con el
 * registro del {@code eventId}, de modo que el reintento (y, agotados, la DLT) vuelve a intentarlo desde cero.
 *
 * <p>Se usa desde un {@code @KafkaListener}: {@code handler.handle(record.value(), event -> useCase.apply(event))}.
 * Las excepciones del efecto se propagan para que el {@code CommonErrorHandler} reintente.
 */
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

    /**
     * @throws MalformedEventException si {@code envelopeJson} no es un sobre de evento válido
     * @return {@code true} si se aplicó el efecto, {@code false} si el evento ya estaba procesado
     */
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

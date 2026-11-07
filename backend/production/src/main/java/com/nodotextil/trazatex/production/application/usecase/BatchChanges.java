package com.nodotextil.trazatex.production.application.usecase;

import com.nodotextil.trazatex.production.application.port.out.BatchRepositoryPort;
import com.nodotextil.trazatex.production.application.port.out.ProductionEventPublisherPort;
import com.nodotextil.trazatex.production.domain.event.BatchPhaseChanged;
import com.nodotextil.trazatex.production.domain.event.ProductionEvent;
import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.BatchPhaseChange;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Único camino para guardar lotes (una regla de ArchUnit prohíbe llamar a {@code BatchRepositoryPort.save} desde otro lado).
 * Garantiza la regla de docs/architecture/communication.md: por cada lote cuya fase o retención por calidad cambie se
 * publica un {@code BatchPhaseChanged}, sea cual sea la operación, además del evento de negocio.
 *
 * <p>Hay dos formas de guardar. {@link #persist} es para lotes que nacen o cuyos datos cambian sin tocar fase ni retención;
 * si alguno trae un cambio sin publicar, falla en voz alta en lugar de perderlo en silencio. {@link #persistAndPublish}
 * es para los que cambiaron: guarda, publica el evento de negocio y después un {@code BatchPhaseChanged} por lote, todo en
 * la transacción del caso de uso (ADR-004).
 */
@Component
class BatchChanges {

    private final BatchRepositoryPort batches;
    private final ProductionEventPublisherPort events;

    BatchChanges(BatchRepositoryPort batches, ProductionEventPublisherPort events) {
        this.batches = batches;
        this.events = events;
    }

    void persist(Batch batch) {
        persist(List.of(batch));
    }

    void persist(Collection<Batch> toSave) {
        for (Batch batch : toSave) {
            if (batch.hasPhaseChanges()) {
                throw new IllegalStateException("El lote " + batch.traceabilityId()
                        + " cambió de fase o retención y se guardó sin publicar BatchPhaseChanged: use persistAndPublish.");
            }
        }
        batches.saveAll(toSave);
    }

    /**
     * Guarda los lotes que cambiaron, publica {@code businessEvent} (si lo hay) y luego un {@code BatchPhaseChanged} por cada
     * cambio, en orden de id de lote (determinista).
     */
    void persistAndPublish(Collection<Batch> changed, PhaseChangeCause cause, ProductionEvent businessEvent) {
        Map<UUID, Long> versions = batches.saveAll(changed);
        if (businessEvent != null) {
            events.publish(businessEvent);
        }
        List<BatchPhaseChange> changes = changed.stream().flatMap(batch -> batch.drainPhaseChanges().stream())
                .sorted(Comparator.comparing((BatchPhaseChange change) -> change.batchId().toString()))
                .toList();
        // Dos cambios del mismo lote en una transacción compartirían versión y el consumidor descartaría uno: fallar en voz alta
        Set<UUID> seen = new HashSet<>();
        for (BatchPhaseChange change : changes) {
            if (!seen.add(change.batchId())) {
                throw new IllegalStateException("El lote " + change.batchId() + " tiene más de un cambio de fase o retención en la misma "
                        + "transacción: compartirían batchVersion y Traceability no podría ordenarlos.");
            }
        }
        for (BatchPhaseChange change : changes) {
            Long version = versions.get(change.batchId());
            if (version == null) {
                throw new IllegalStateException("No se obtuvo la versión del lote " + change.batchId() + " tras guardarlo.");
            }
            events.publish(BatchPhaseChanged.of(change, cause.reason(), cause.requestId(), version));
        }
    }
}

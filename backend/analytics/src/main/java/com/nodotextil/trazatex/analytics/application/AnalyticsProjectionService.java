package com.nodotextil.trazatex.analytics.application;

import com.nodotextil.trazatex.analytics.application.port.out.AnalyticsProjectionPort;
import com.nodotextil.trazatex.analytics.application.port.out.AnalyticsProjectionPort.RecordedEvent;
import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedmessaging.consumer.EventEnvelopes;
import com.nodotextil.trazatex.sharedmessaging.consumer.MalformedEventException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Alimenta el modelo de lectura (CQRS) con los eventos de Production y Quality. Cada evento se guarda tal cual llegó y, si cambia un lote,
 * su nacimiento, su empresa dueña o su estado de calidad.
 * <p>
 * <b>Orden.</b> Los tópicos llegan en cualquier orden entre sí y el reloj de cada servicio no es monótono, así que la empresa dueña no se
 * decide por la hora: es la del {@code BatchPhaseChanged} con mayor {@code batchVersion} (communication.md). Un evento de creación nunca pisa
 * una versión mayor. El resultado de reprocesar todo el histórico, en cualquier orden, es el mismo (ver la prueba de reconstrucción).
 * <p>
 * Un evento repetido ({@code event_id} ya guardado) no cambia nada. Uno ilegible o sin los datos que necesita lanza
 * {@link MalformedEventException}: va a la {@code .DLT} sin reintentos, y la transacción no deja nada a medias.
 */
@Service
public class AnalyticsProjectionService {
    private static final Set<String> TRANSFERS = Set.of("TransferInitiated", "TransferAccepted", "TransferRejected");

    private final AnalyticsProjectionPort store;
    private final ObjectMapper mapper;
    public AnalyticsProjectionService(AnalyticsProjectionPort store, ObjectMapper mapper) { this.store=store; this.mapper=mapper; }

    @Transactional
    public void consume(String json) {
        BaseDomainEvent e = EventEnvelopes.parse(mapper, json);
        JsonNode p = mapper.valueToTree(e.payload());
        UUID company = optionalUuid(e, p, TRANSFERS.contains(e.type()) ? "fromCompanyId" : "companyId");
        if (!store.record(new RecordedEvent(e.eventId(), e.type(), e.aggregateId(), e.occurredAt(), company, businessAt(e, p), p.toString()))) return;
        switch (e.type()) {
            case "BatchRegistered" -> store.birth(e.eventId(), uuid(e, p, "batchId"), uuid(e, p, "companyId"), e.occurredAt(), "NOT_REVIEWED");
            case "BatchSplit" -> {
                UUID owner = uuid(e, p, "companyId");
                for (JsonNode child : array(e, p, "children"))
                    store.birth(e.eventId(), uuid(e, child, "batchId"), owner, e.occurredAt(), child.path("qualityStatus").asString("NOT_REVIEWED"));
            }
            case "TransformationCompleted" -> {
                UUID owner = uuid(e, p, "companyId");
                for (JsonNode output : array(e, p, "outputs"))
                    store.birth(e.eventId(), uuid(e, output, "batchId"), owner, e.occurredAt(), "NOT_REVIEWED");
            }
            case "BatchPhaseChanged" -> {
                if (!p.path("batchVersion").canConvertToLong()) throw malformed(e, "batchVersion");
                store.owner(e.eventId(), uuid(e, p, "batchId"), uuid(e, p, "companyId"), e.occurredAt(), p.path("batchVersion").asLong());
            }
            case "QualityStatusChanged" -> {
                String status = p.path("newStatus").asString("");
                if (status.isBlank()) throw malformed(e, "newStatus");
                store.quality(e.eventId(), uuid(e, p, "batchId"), status, e.occurredAt());
            }
            default -> { /* solo se guarda: QualityFailureUpdated, QualityControlCompleted, Transfer*, FailureConfirmed... */ }
        }
    }

    /** Cuándo ocurrió el hecho: la fecha de negocio del payload si la trae (finalización o aceptación), si no la del evento. */
    private Instant businessAt(BaseDomainEvent e, JsonNode p) {
        String field = switch (e.type()) {
            case "TransformationCompleted" -> "completedAt";
            case "QualityControlCompleted" -> "finishedAt";
            case "TransferAccepted" -> "acceptedAt";
            default -> null;
        };
        if (field == null || p.path(field).isMissingNode() || p.path(field).isNull()) return e.occurredAt();
        try {
            return OffsetDateTime.parse(p.path(field).asString("")).toInstant();
        } catch (RuntimeException ex) {
            throw new MalformedEventException("El campo " + field + " de " + e.type() + " (" + e.eventId() + ") no es una fecha válida", ex);
        }
    }

    private static UUID optionalUuid(BaseDomainEvent e, JsonNode p, String field) {
        return p.path(field).isMissingNode() || p.path(field).isNull() ? null : uuid(e, p, field);
    }
    private static UUID uuid(BaseDomainEvent e, JsonNode node, String field) {
        try {
            return UUID.fromString(node.path(field).asString(""));
        } catch (IllegalArgumentException ex) {
            throw malformed(e, field);
        }
    }
    private static JsonNode array(BaseDomainEvent e, JsonNode p, String field) {
        if (!p.path(field).isArray()) throw malformed(e, field);
        return p.path(field);
    }
    private static MalformedEventException malformed(BaseDomainEvent e, String field) {
        return new MalformedEventException("El payload de " + e.type() + " (" + e.eventId() + ") no trae «" + field + "» válido", null);
    }
}

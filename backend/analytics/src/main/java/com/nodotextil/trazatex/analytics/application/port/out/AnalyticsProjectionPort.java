package com.nodotextil.trazatex.analytics.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Escritura del modelo de lectura. Cada método es idempotente por su clave; el servicio los llama dentro de una sola transacción. */
public interface AnalyticsProjectionPort {

    /** Un evento tal como llegó. {@code companyId} es la empresa a la que el indicador atribuye el hecho; {@code businessAt}, cuándo ocurrió. */
    record RecordedEvent(UUID eventId, String type, String aggregateId, Instant occurredAt, UUID companyId, Instant businessAt, String payloadJson) { }

    /** @return {@code false} si el evento ya estaba guardado (entrega repetida): no hay que volver a aplicarlo */
    boolean record(RecordedEvent event);

    /** Nacimiento de un lote: su empresa, su fecha de registro y su estado inicial. No reemplaza uno ya guardado. */
    void birth(UUID eventId, UUID batchId, UUID companyId, Instant at, String qualityStatus);

    /** Empresa dueña tras un {@code BatchPhaseChanged}. La vigente es la de mayor {@code batchVersion}; el reloj solo desempata. */
    void owner(UUID eventId, UUID batchId, UUID companyId, Instant at, long batchVersion);

    /** Cambio de estado de calidad de un lote ({@code QualityStatusChanged}). */
    void quality(UUID eventId, UUID batchId, String status, Instant at);
}

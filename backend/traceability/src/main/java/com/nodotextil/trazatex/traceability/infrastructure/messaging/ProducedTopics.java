package com.nodotextil.trazatex.traceability.infrastructure.messaging;

/**
 * Tópicos que traceability produce (docs/architecture/communication.md): los declara él, sin borrado por tiempo ({@code retention.ms=-1}) como los demás
 * tópicos de eventos de negocio.
 */
public final class ProducedTopics {

    /** {@code PossibleDerivedFailureDetected}, clave {@code failureId}. */
    public static final String IMPACT = "traceability.impact";

    private ProducedTopics() {
    }
}

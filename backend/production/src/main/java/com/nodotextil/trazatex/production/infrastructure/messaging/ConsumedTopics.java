package com.nodotextil.trazatex.production.infrastructure.messaging;

import com.nodotextil.trazatex.sharedmessaging.consumer.TopicWatcher;

import java.util.List;

/**
 * Tópicos que consume production (docs/architecture/communication.md). Son de otros servicios: production declara solo sus
 * {@code .DLT} y nunca los crea (el de Quality puede no existir todavía y el consumidor lo tolera).
 */
public final class ConsumedTopics {

    public static final String ORGACCESS_COMPANY = "orgaccess.company";
    public static final String QUALITY_CONTROL = "quality.control";

    /** Id del contenedor que lee {@link #QUALITY_CONTROL}: arranca solo cuando el tópico existe (ver {@link TopicWatcherJob}). */
    public static final String QUALITY_LISTENER_ID = "production-quality-control";

    /** Los contenedores que vigila el {@code TopicWatcher} de {@code shared-messaging}. */
    public static final List<TopicWatcher.Source> ALL = List.of(new TopicWatcher.Source(QUALITY_LISTENER_ID, QUALITY_CONTROL));

    private ConsumedTopics() {
    }
}

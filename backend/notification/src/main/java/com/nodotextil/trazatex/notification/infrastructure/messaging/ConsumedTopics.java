package com.nodotextil.trazatex.notification.infrastructure.messaging;

import com.nodotextil.trazatex.sharedmessaging.consumer.TopicWatcher;
import java.util.List;

/**
 * Tópicos que consume Notification (docs/architecture/communication.md). Son de otros servicios: Notification declara solo sus {@code .DLT} y
 * <b>nunca</b> los crea. Cada tópico tiene su propio grupo y su propio contenedor, que no arranca hasta que el tópico existe (ver
 * {@link TopicWatcherJob}): un mensaje en reintento en un tópico no frena a los demás, y un tópico ausente no llena el log.
 */
public final class ConsumedTopics {
    public static final String ORGACCESS_USER = "orgaccess.user";
    public static final String ORGACCESS_INVITATION = "orgaccess.invitation";
    public static final String PRODUCTION_TRANSFER = "production.transfer";
    public static final String TRACEABILITY_IMPACT = "traceability.impact";

    public static final String USER_LISTENER_ID = "notification-orgaccess-user";
    public static final String INVITATION_LISTENER_ID = "notification-orgaccess-invitation";
    public static final String TRANSFER_LISTENER_ID = "notification-production-transfer";
    public static final String IMPACT_LISTENER_ID = "notification-traceability-impact";

    public static final List<TopicWatcher.Source> ALL = List.of(
            new TopicWatcher.Source(USER_LISTENER_ID, ORGACCESS_USER),
            new TopicWatcher.Source(INVITATION_LISTENER_ID, ORGACCESS_INVITATION),
            new TopicWatcher.Source(TRANSFER_LISTENER_ID, PRODUCTION_TRANSFER),
            new TopicWatcher.Source(IMPACT_LISTENER_ID, TRACEABILITY_IMPACT));

    private ConsumedTopics() { }
}

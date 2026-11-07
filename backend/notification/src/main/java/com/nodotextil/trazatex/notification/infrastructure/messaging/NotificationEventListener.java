package com.nodotextil.trazatex.notification.infrastructure.messaging;

import com.nodotextil.trazatex.notification.application.NotificationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Un contenedor y un grupo por tópico, todos con {@code autoStartup = "false"}: los arranca el {@link TopicWatcherJob} cuando el tópico
 * existe. Los grupos son nuevos (leen desde {@code earliest}) y {@code processed_events} descarta lo ya aplicado.
 */
@Component
@ConditionalOnProperty(name = "trazatex.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationEventListener {
    private final NotificationService notifications;
    public NotificationEventListener(NotificationService notifications) { this.notifications=notifications; }

    @KafkaListener(id=ConsumedTopics.USER_LISTENER_ID, topics=ConsumedTopics.ORGACCESS_USER, autoStartup="false",
            groupId="${trazatex.messaging.consumer.user.group:notification.orgaccess-user}")
    public void onUser(String json) { notifications.consume(json); }

    @KafkaListener(id=ConsumedTopics.INVITATION_LISTENER_ID, topics=ConsumedTopics.ORGACCESS_INVITATION, autoStartup="false",
            groupId="${trazatex.messaging.consumer.invitation.group:notification.orgaccess-invitation}")
    public void onInvitation(String json) { notifications.consume(json); }

    @KafkaListener(id=ConsumedTopics.TRANSFER_LISTENER_ID, topics=ConsumedTopics.PRODUCTION_TRANSFER, autoStartup="false",
            groupId="${trazatex.messaging.consumer.transfer.group:notification.production-transfer}")
    public void onTransfer(String json) { notifications.consume(json); }

    @KafkaListener(id=ConsumedTopics.IMPACT_LISTENER_ID, topics=ConsumedTopics.TRACEABILITY_IMPACT, autoStartup="false",
            groupId="${trazatex.messaging.consumer.impact.group:notification.traceability-impact}")
    public void onImpact(String json) { notifications.consume(json); }
}

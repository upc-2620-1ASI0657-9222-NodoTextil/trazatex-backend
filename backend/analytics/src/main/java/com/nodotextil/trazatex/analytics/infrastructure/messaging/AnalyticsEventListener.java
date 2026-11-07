package com.nodotextil.trazatex.analytics.infrastructure.messaging;

import com.nodotextil.trazatex.analytics.application.AnalyticsProjectionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Un contenedor y un grupo por tópico, todos con {@code autoStartup = "false"}: los arranca el {@link TopicWatcherJob} cuando el tópico
 * existe. Sin lógica: cada mensaje va al servicio de proyección, que es idempotente por {@code eventId}.
 */
@Component
@ConditionalOnProperty(name = "trazatex.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class AnalyticsEventListener {
    private final AnalyticsProjectionService projection;
    public AnalyticsEventListener(AnalyticsProjectionService projection) { this.projection=projection; }

    @KafkaListener(id=ConsumedTopics.BATCH_LISTENER_ID, topics=ConsumedTopics.PRODUCTION_BATCH, autoStartup="false",
            groupId="${trazatex.messaging.consumer.batch.group:analytics.production-batch}")
    public void onBatch(String json) { projection.consume(json); }

    @KafkaListener(id=ConsumedTopics.TRANSFORMATION_LISTENER_ID, topics=ConsumedTopics.PRODUCTION_TRANSFORMATION, autoStartup="false",
            groupId="${trazatex.messaging.consumer.transformation.group:analytics.production-transformation}")
    public void onTransformation(String json) { projection.consume(json); }

    @KafkaListener(id=ConsumedTopics.TRANSFER_LISTENER_ID, topics=ConsumedTopics.PRODUCTION_TRANSFER, autoStartup="false",
            groupId="${trazatex.messaging.consumer.transfer.group:analytics.production-transfer}")
    public void onTransfer(String json) { projection.consume(json); }

    @KafkaListener(id=ConsumedTopics.CONTROL_LISTENER_ID, topics=ConsumedTopics.QUALITY_CONTROL, autoStartup="false",
            groupId="${trazatex.messaging.consumer.control.group:analytics.quality-control}")
    public void onControl(String json) { projection.consume(json); }

    @KafkaListener(id=ConsumedTopics.FAILURE_LISTENER_ID, topics=ConsumedTopics.QUALITY_FAILURE, autoStartup="false",
            groupId="${trazatex.messaging.consumer.failure.group:analytics.quality-failure}")
    public void onFailure(String json) { projection.consume(json); }
}

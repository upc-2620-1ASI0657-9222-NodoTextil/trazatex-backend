package com.nodotextil.trazatex.quality.infrastructure.messaging;

import com.nodotextil.trazatex.quality.application.port.out.QualityEventPublisherPort;
import com.nodotextil.trazatex.quality.domain.event.FailureConfirmed;
import com.nodotextil.trazatex.quality.domain.event.QualityControlCompleted;
import com.nodotextil.trazatex.quality.domain.event.QualityEvent;
import com.nodotextil.trazatex.quality.domain.event.QualityStatusChanged;
import com.nodotextil.trazatex.sharedkernel.event.BaseDomainEvent;
import com.nodotextil.trazatex.sharedkernel.outbox.OutboxEventRecorder;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
class OutboxQualityEventPublisher implements QualityEventPublisherPort {

    private final OutboxEventRecorder recorder;
    private final Clock clock;

    OutboxQualityEventPublisher(OutboxEventRecorder recorder, Clock clock) {
        this.recorder = recorder;
        this.clock = clock;
    }

    @Override
    public void publish(QualityEvent event) {
        recorder.record(aggregateType(event), topic(event), BaseDomainEvent.of(event.eventType(), event.aggregateId(), event, clock));
    }

    private static String aggregateType(QualityEvent event) {
        return switch (event) {
            case QualityControlCompleted e -> "QualityControl";
            case QualityStatusChanged e -> "Batch";
            case FailureConfirmed e -> "QualityFailure";
            case com.nodotextil.trazatex.quality.domain.event.QualityFailureUpdated e -> "QualityFailure";
            default -> throw new IllegalArgumentException("Evento sin ruta: " + event.eventType());
        };
    }

    private static String topic(QualityEvent event) {
        return switch (event) {
            case QualityControlCompleted e -> Topics.CONTROL;
            case QualityStatusChanged e -> Topics.CONTROL;
            case FailureConfirmed e -> Topics.FAILURE;
            case com.nodotextil.trazatex.quality.domain.event.QualityFailureUpdated e -> Topics.FAILURE;
            default -> throw new IllegalArgumentException("Evento sin ruta: " + event.eventType());
        };
    }
}

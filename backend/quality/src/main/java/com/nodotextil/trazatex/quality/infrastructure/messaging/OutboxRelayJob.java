package com.nodotextil.trazatex.quality.infrastructure.messaging;

import com.nodotextil.trazatex.sharedkernel.outbox.OutboxRelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Programa el relay del outbox (~1 s). Cada pasada publica en orden las filas {@code PENDING} y marca las confirmadas por el
 * broker; lo que falla se reintenta en la pasada siguiente (se ve en {@code attempts} y {@code last_error}).
 */
@Component
@ConditionalOnProperty(name = "trazatex.messaging.enabled", havingValue = "true", matchIfMissing = true)
class OutboxRelayJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayJob.class);

    private final OutboxRelay relay;

    OutboxRelayJob(OutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${trazatex.messaging.relay-delay:PT1S}")
    void publishPending() {
        OutboxRelay.Result result = relay.publishPending();
        if (result.published() > 0) {
            log.debug("Outbox: {} eventos publicados", result.published());
        }
    }
}

package com.nodotextil.trazatex.quality.infrastructure.scheduling;

import com.nodotextil.trazatex.sharedkernel.idempotency.IdempotencyFilter;
import com.nodotextil.trazatex.sharedkernel.idempotency.IdempotencyStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** Las claves de idempotencia caducan a las 24 h (ADR-010). */
@Component
class IdempotencyKeyCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyCleanupJob.class);

    private final IdempotencyStorePort store;
    private final Clock clock;

    IdempotencyKeyCleanupJob(IdempotencyStorePort store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${trazatex.idempotency.cleanup-job-delay}")
    @Transactional
    void deleteExpiredKeys() {
        int deleted = store.deleteCreatedBefore(clock.instant().minus(IdempotencyFilter.DEFAULT_RETENTION));
        if (deleted > 0) {
            log.info("Claves de idempotencia caducadas eliminadas: {}", deleted);
        }
    }
}

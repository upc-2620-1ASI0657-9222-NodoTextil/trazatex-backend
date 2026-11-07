package com.nodotextil.trazatex.quality.application.usecase;

import com.nodotextil.trazatex.quality.application.port.out.ClaimedDiscard;
import com.nodotextil.trazatex.quality.application.port.out.ClaimedHold;
import com.nodotextil.trazatex.quality.application.port.out.ClaimedStart;
import com.nodotextil.trazatex.quality.application.port.out.QualityControlRepositoryPort;
import com.nodotextil.trazatex.quality.application.port.out.QualityFailureRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;

/**
 * Las cuatro fuentes. Cada una reclama en una transacción corta ({@code FOR UPDATE SKIP LOCKED}, que además arrienda lo reclamado) y resuelve
 * fuera de ella, uno por uno; un elemento que falla no frena a los demás de su fuente (su arrendamiento vence y se repite).
 */
final class PendingCommandSources {

    private static final Logger log = LoggerFactory.getLogger(PendingCommandSources.class);

    private PendingCommandSources() {
    }

    private static TransactionTemplate requiresNew(PlatformTransactionManager tm) {
        TransactionTemplate template = new TransactionTemplate(tm);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    @Component
    static class Starts implements PendingCommandSource {

        private final QualityControlRepositoryPort controls;
        private final ControlStartResolver resolver;
        private final PendingCommandSettings settings;
        private final Clock clock;
        private final TransactionTemplate tx;

        Starts(QualityControlRepositoryPort controls, ControlStartResolver resolver, PendingCommandSettings settings, Clock clock, PlatformTransactionManager tm) {
            this.controls = controls;
            this.resolver = resolver;
            this.settings = settings;
            this.clock = clock;
            this.tx = requiresNew(tm);
        }

        @Override
        public Kind kind() {
            return Kind.START;
        }

        @Override
        public int recover() {
            List<ClaimedStart> claimed = tx.execute(s -> controls.claimPendingStarts(clock.instant(), settings.claimsPerRun(), settings.lease()));
            for (ClaimedStart start : claimed) {
                try {
                    log.info("Control {} del lote {}: {}", start.control().id(), start.control().batchId(), resolver.resolve(start.control(), start.attempts()).kind());
                } catch (RuntimeException e) {
                    log.error("No se pudo resolver el inicio del control {} (se repetirá al vencer su arrendamiento)", start.control().id(), e);
                }
            }
            return claimed.size();
        }
    }

    @Component
    static class Finishes implements PendingCommandSource {

        private final QualityControlRepositoryPort controls;
        private final ControlFinishResolver resolver;
        private final PendingCommandSettings settings;
        private final Clock clock;
        private final TransactionTemplate tx;

        Finishes(QualityControlRepositoryPort controls, ControlFinishResolver resolver, PendingCommandSettings settings, Clock clock, PlatformTransactionManager tm) {
            this.controls = controls;
            this.resolver = resolver;
            this.settings = settings;
            this.clock = clock;
            this.tx = requiresNew(tm);
        }

        @Override
        public Kind kind() {
            return Kind.FINISH;
        }

        @Override
        public int recover() {
            List<ClaimedStart> claimed = tx.execute(s -> controls.claimPendingFinishes(clock.instant(), settings.claimsPerRun(), settings.lease()));
            for (ClaimedStart finish : claimed) {
                try {
                    log.info("Finalización del control {} del lote {}: {}", finish.control().id(), finish.control().batchId(),
                            resolver.resolve(finish.control(), finish.attempts()).kind());
                } catch (RuntimeException e) {
                    log.error("No se pudo resolver la finalización del control {} (se repetirá al vencer su arrendamiento)", finish.control().id(), e);
                }
            }
            return claimed.size();
        }
    }

    @Component
    static class Discards implements PendingCommandSource {

        private final QualityFailureRepositoryPort failures;
        private final FailureDiscardResolver resolver;
        private final PendingCommandSettings settings;
        private final Clock clock;
        private final TransactionTemplate tx;

        Discards(QualityFailureRepositoryPort failures, FailureDiscardResolver resolver, PendingCommandSettings settings, Clock clock, PlatformTransactionManager tm) {
            this.failures = failures;
            this.resolver = resolver;
            this.settings = settings;
            this.clock = clock;
            this.tx = requiresNew(tm);
        }

        @Override
        public Kind kind() {
            return Kind.DISCARD;
        }

        @Override
        public int recover() {
            List<ClaimedDiscard> claimed = tx.execute(s -> failures.claimPendingDiscards(clock.instant(), settings.claimsPerRun(), settings.lease()));
            for (ClaimedDiscard discard : claimed) {
                try {
                    log.info("Descarte de la falla {} del lote {}: {}", discard.failure().id(), discard.failure().batchId(),
                            resolver.resolve(discard.failure(), discard.attempts()).kind());
                } catch (RuntimeException e) {
                    log.error("No se pudo resolver el descarte de la falla {} (se repetirá al vencer su arrendamiento)", discard.failure().id(), e);
                }
            }
            return claimed.size();
        }
    }

    @Component
    static class Holds implements PendingCommandSource {

        private final DerivedHoldResolver resolver;

        Holds(DerivedHoldResolver resolver) {
            this.resolver = resolver;
        }

        @Override
        public Kind kind() {
            return Kind.HOLD;
        }

        @Override
        public int recover() {
            List<ClaimedHold> claimed = resolver.claim();
            for (ClaimedHold hold : claimed) {
                try {
                    resolver.resolve(hold);
                } catch (RuntimeException e) {
                    log.error("No se pudo resolver el aviso de la falla {} sobre el lote {} (se repetirá al vencer su arrendamiento)",
                            hold.impacted().failureId(), hold.impacted().batchId(), e);
                }
            }
            return claimed.size();
        }
    }
}

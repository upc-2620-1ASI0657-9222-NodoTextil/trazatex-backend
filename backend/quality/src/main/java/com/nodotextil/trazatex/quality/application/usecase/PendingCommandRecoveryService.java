package com.nodotextil.trazatex.quality.application.usecase;

import com.nodotextil.trazatex.quality.application.port.in.RecoverPendingCommandsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
class PendingCommandRecoveryService implements RecoverPendingCommandsUseCase {

    private static final Logger log = LoggerFactory.getLogger(PendingCommandRecoveryService.class);

    private final List<PendingCommandSource> sources;

    PendingCommandRecoveryService(List<PendingCommandSource> sources) {
        this.sources = List.copyOf(sources);
    }

    @Override
    public int recoverPending() {
        return run(Set.of(PendingCommandSource.Kind.START, PendingCommandSource.Kind.FINISH, PendingCommandSource.Kind.DISCARD));
    }

    @Override
    public int recoverPendingHolds() {
        return run(Set.of(PendingCommandSource.Kind.HOLD));
    }

    @Override
    public int recoverAll() {
        return run(Set.of(PendingCommandSource.Kind.values()));
    }

    private int run(Set<PendingCommandSource.Kind> kinds) {
        int claimed = 0;
        for (PendingCommandSource source : sources) {
            if (!kinds.contains(source.kind())) {
                continue;
            }
            try {
                claimed += source.recover();
            } catch (RuntimeException e) {
                log.error("Falló la fuente {} de los comandos pendientes a Production; las demás siguen", source.kind(), e);
            }
        }
        return claimed;
    }
}

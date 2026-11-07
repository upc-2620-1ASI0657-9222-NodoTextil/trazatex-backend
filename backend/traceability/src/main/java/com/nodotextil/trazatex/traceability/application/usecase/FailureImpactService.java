package com.nodotextil.trazatex.traceability.application.usecase;

import com.nodotextil.trazatex.traceability.application.dto.AnalyzeFailureImpactCommand;
import com.nodotextil.trazatex.traceability.application.port.in.AnalyzeFailureImpactUseCase;
import com.nodotextil.trazatex.traceability.application.port.out.ImpactEventPort;
import com.nodotextil.trazatex.traceability.application.port.out.LineageReadPort;
import com.nodotextil.trazatex.traceability.domain.model.impact.ImpactResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * US33: al confirmarse una falla, calcula su familia ({@link ImpactAnalyzer}) y registra {@code PossibleDerivedFailureDetected} en el outbox, en la transacción
 * del evento. No publica nada si no hay lotes afectados ni una segunda vez para la misma falla; si la familia está incompleta, la excepción hace que se revierta todo
 * y el evento se reintente.
 */
@Service
@Transactional
class FailureImpactService implements AnalyzeFailureImpactUseCase {

    private static final Logger log = LoggerFactory.getLogger(FailureImpactService.class);

    private final ImpactAnalyzer analyzer;
    private final ImpactEventPort events;

    FailureImpactService(LineageReadPort port, ImpactEventPort events) {
        this.analyzer = new ImpactAnalyzer(port);
        this.events = events;
    }

    @Override
    public void apply(AnalyzeFailureImpactCommand command) {
        if (events.alreadyRecorded(command.failureId())) {
            log.info("La falla {} ya tiene su PossibleDerivedFailureDetected: no se publica otra vez", command.failureId());
            return;
        }
        ImpactResult result = analyzer.analyze(command.sourceBatchId());
        if (result.truncated()) {
            log.warn("La familia del lote {} (falla {}) sigue más allá de 10 niveles: se publica solo lo que entra en el límite", command.sourceBatchId(), command.failureId());
        }
        if (result.affected().isEmpty()) {
            log.info("La falla {} (lote {}) no tiene lotes afectados: no se publica nada", command.failureId(), command.sourceBatchId());
            return;
        }
        events.record(command.failureId(), result);
        log.info("La falla {} (lote {}): {} lotes posiblemente afectados", command.failureId(), command.sourceBatchId(), result.affected().size());
    }
}

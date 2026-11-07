package com.nodotextil.trazatex.production.domain.event;

import com.nodotextil.trazatex.production.domain.model.BatchPhase;
import com.nodotextil.trazatex.production.domain.model.BatchPhaseChange;

import java.util.UUID;

/**
 * Tópico {@code production.batch}, clave batchId. Se publica por cada lote cuya fase o retención por calidad cambie, sea
 * cual sea la operación, además del evento de negocio (regla en docs/architecture/communication.md). {@code reason} y
 * {@code requestId} dicen qué lo provocó: en las operaciones del usuario, el código de la operación y el id de la
 * transformación, la transferencia o el lote padre; en los comandos de Quality, los del comando.
 *
 * <p>{@code batchVersion} es el {@code @Version} del lote <b>después</b> del cambio. Crece en cada cambio de fase o retención
 * del mismo lote (y también con cambios que no publican este evento, como {@code QualityStatusChanged}: puede haber saltos,
 * nunca retrocesos). Es lo que usan los consumidores para ordenar los cambios de un lote entre tópicos sin depender del reloj.
 */
public record BatchPhaseChanged(
        UUID batchId,
        UUID companyId,
        BatchPhase previousPhase,
        BatchPhase newPhase,
        boolean qualityHold,
        String reason,
        String requestId,
        long batchVersion) implements ProductionEvent {

    public static final String TYPE = "BatchPhaseChanged";

    public static BatchPhaseChanged of(BatchPhaseChange change, String reason, String requestId, long batchVersion) {
        return new BatchPhaseChanged(change.batchId(), change.companyId(), change.previousPhase(), change.newPhase(),
                change.qualityHold(), reason, requestId, batchVersion);
    }

    @Override
    public String eventType() {
        return TYPE;
    }

    @Override
    public String aggregateId() {
        return batchId.toString();
    }
}

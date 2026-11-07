package com.nodotextil.trazatex.traceability.application.port.out;

import com.nodotextil.trazatex.traceability.domain.model.graph.QualityTestValue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Escritura de los detalles de calidad de un lote en el grafo. Si el lote aún no existe se crea provisional (su evento de creación lo completa). Gana el
 * dato del evento más reciente por {@code occurredAt}: uno que llega tarde con un instante anterior no pisa al guardado.
 */
public interface QualityDetailsPort {

    void saveTests(UUID batchId, Instant occurredAt, List<QualityTestValue> tests);

    void saveFailureCause(UUID batchId, Instant occurredAt, String cause);
}

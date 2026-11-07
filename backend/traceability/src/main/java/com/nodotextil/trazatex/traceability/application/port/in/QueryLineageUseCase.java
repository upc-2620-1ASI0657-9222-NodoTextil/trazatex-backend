package com.nodotextil.trazatex.traceability.application.port.in;

import com.nodotextil.trazatex.traceability.domain.model.view.VisibleBatchPage;
import com.nodotextil.trazatex.traceability.domain.model.view.VisibleLineage;

import java.util.UUID;

/**
 * Consultas de genealogía (US30–US32, US36). Todo lo que devuelve son tipos de {@code domain.model.view}: ya pasaron por la política de
 * privacidad (lo exige una regla de ArchUnit). {@code requesterCompanyId} es la empresa de quien consulta.
 */
public interface QueryLineageUseCase {

    /** @throws com.nodotextil.trazatex.sharedkernel.exception.NotFoundException {@code BATCH_NOT_FOUND}: desconocido o incompleto, sin distinguirlo */
    VisibleLineage ancestors(UUID batchId, UUID requesterCompanyId);

    VisibleLineage descendants(UUID batchId, UUID requesterCompanyId);

    /** Ambas direcciones, con el historial de transferencias de cada lote. */
    VisibleLineage lineage(UUID batchId, UUID requesterCompanyId);

    /**
     * Busca por {@code qrCode} (exacto, de cualquier empresa) y/o {@code traceabilityId} (por prefijo solo en los lotes de la propia empresa;
     * en los de otra, solo coincidencia exacta). Con ambos deben cumplirse los dos. {@code page} desde 0 (por defecto 0) y {@code size}
     * (por defecto 20, máximo 100).
     */
    VisibleBatchPage search(UUID requesterCompanyId, String qrCode, String traceabilityId, Integer page, Integer size);
}

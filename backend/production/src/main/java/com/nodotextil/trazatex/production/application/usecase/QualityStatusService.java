package com.nodotextil.trazatex.production.application.usecase;

import com.nodotextil.trazatex.production.application.port.in.ApplyQualityStatusUseCase;
import com.nodotextil.trazatex.production.application.port.out.BatchRepositoryPort;
import com.nodotextil.trazatex.production.domain.exception.ErrorCodes;
import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.QualityStatus;
import com.nodotextil.trazatex.sharedkernel.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Copia de {@code qualityStatus} para mostrar. La fase y la retención no cambian aquí (las fija el comando interno), así que
 * no se publica {@code BatchPhaseChanged}; igual se guarda por {@link BatchChanges}, que lo comprueba. El lote se lee <b>sin</b>
 * bloqueo de fila: si una operación del usuario lo modifica entre la lectura y el guardado, el bloqueo optimista
 * ({@code @Version}) lanza un conflicto, la transacción se revierte (también el registro en {@code processed_events}) y el
 * consumidor lo reintenta con espera creciente.
 */
@Service
@Transactional
class QualityStatusService implements ApplyQualityStatusUseCase {

    private static final Logger log = LoggerFactory.getLogger(QualityStatusService.class);

    private final BatchRepositoryPort batches;
    private final BatchChanges batchChanges;

    QualityStatusService(BatchRepositoryPort batches, BatchChanges batchChanges) {
        this.batches = batches;
        this.batchChanges = batchChanges;
    }

    @Override
    public void apply(UUID batchId, UUID companyId, QualityStatus newStatus, UUID controlId) {
        Batch batch = batches.findById(batchId).orElseThrow(() -> new NotFoundException(ErrorCodes.BATCH_NOT_FOUND,
                "El lote " + batchId + " del control " + controlId + " no existe."));
        if (!batch.companyId().equals(companyId)) {
            log.warn("QualityStatusChanged del lote {} (control {}) trae la empresa {} pero hoy es de {}: el lote cambió de empresa; "
                    + "se aplica igual", batchId, controlId, companyId, batch.companyId());
        }
        if (batch.qualityStatus() == newStatus) {
            return;
        }
        batch.showQualityStatus(newStatus);
        batchChanges.persist(batch);
    }
}

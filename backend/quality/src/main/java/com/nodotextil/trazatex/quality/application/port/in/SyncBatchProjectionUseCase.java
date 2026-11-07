package com.nodotextil.trazatex.quality.application.port.in;

import com.nodotextil.trazatex.quality.application.dto.OutputBatch;
import com.nodotextil.trazatex.quality.application.dto.SplitChild;
import com.nodotextil.trazatex.quality.domain.model.BatchPhase;

import java.util.List;
import java.util.UUID;

/** Mantiene la proyección local de lotes con los eventos de Production (nunca por REST). */
public interface SyncBatchProjectionUseCase {

    /** {@code BatchRegistered}: nace {@code AVAILABLE}, sin retención y {@code NOT_REVIEWED}. */
    void registered(UUID batchId, UUID companyId, String traceabilityId);

    default void registered(UUID batchId, UUID companyId) {
        registered(batchId, companyId, null);
    }

    /** {@code BatchSplit}: da a conocer a los hijos, que nacen con el estado de calidad del padre. */
    void split(UUID companyId, List<SplitChild> children);

    /** {@code TransformationCompleted}: da a conocer a las salidas, que nacen {@code AVAILABLE} y {@code NOT_REVIEWED}. */
    void transformationOutputs(UUID companyId, List<OutputBatch> outputs);

    /** {@code BatchPhaseChanged}: se aplica solo si trae una {@code batchVersion} mayor que la guardada. */
    void phaseChanged(UUID batchId, UUID companyId, BatchPhase newPhase, boolean qualityHold, long batchVersion);

    /** {@code TransferAccepted}: los lotes pasan a la empresa destino. */
    void transferAccepted(List<UUID> batchIds, UUID fromCompanyId, UUID toCompanyId);
}

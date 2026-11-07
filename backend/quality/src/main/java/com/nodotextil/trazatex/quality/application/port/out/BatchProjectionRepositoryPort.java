package com.nodotextil.trazatex.quality.application.port.out;

import com.nodotextil.trazatex.quality.domain.model.BatchProjection;
import com.nodotextil.trazatex.sharedkernel.paging.PageRequest;
import com.nodotextil.trazatex.sharedkernel.paging.PageResult;

import java.util.Optional;
import java.util.UUID;

/**
 * La proyección local de lotes ({@code batch_projection}). Los cambios de un mismo lote se serializan con el bloqueo de su fila
 * ({@code SELECT ... FOR UPDATE}).
 */
public interface BatchProjectionRepositoryPort {

    Optional<BatchProjection> findByIdForUpdate(UUID batchId);

    Optional<BatchProjection> findById(UUID batchId);

    /** Una página de los lotes de la empresa que ya tienen estado de calidad, por id (orden estable). */
    PageResult<BatchProjection> findQualityPageByCompany(UUID companyId, PageRequest request);

    /**
     * Inserta un lote nuevo. Si otra transacción lo insertó a la vez la clave primaria falla y el consumo se reintenta (al reintentar
     * el lote ya existe y se completa).
     */
    void insert(BatchProjection batch);

    /** Actualiza un lote que se leyó con {@link #findByIdForUpdate}. */
    void update(BatchProjection batch);
}

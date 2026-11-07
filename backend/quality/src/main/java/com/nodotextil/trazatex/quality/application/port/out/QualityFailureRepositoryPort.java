package com.nodotextil.trazatex.quality.application.port.out;

import com.nodotextil.trazatex.quality.domain.model.QualityFailure;
import com.nodotextil.trazatex.sharedkernel.paging.PageRequest;
import com.nodotextil.trazatex.sharedkernel.paging.PageResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QualityFailureRepositoryPort {

    /** Una falla nueva {@code OPEN}. Un lote tiene a lo sumo una abierta (índice único). */
    void insert(QualityFailure failure, Instant createdAt);

    Optional<QualityFailure> findById(UUID failureId);

    /** La falla cuyo último control es este (la que generó o reabrió). */
    Optional<QualityFailure> findByControlId(UUID controlId);

    Optional<QualityFailure> findByIdForUpdate(UUID failureId);

    /** Una página de las fallas de la empresa, de la más reciente a la más antigua ({@code created_at} descendente y, a igual fecha, {@code id}). */
    PageResult<QualityFailure> findPageByCompany(UUID companyId, PageRequest request);

    /** La falla abierta del lote, con la fila bloqueada. */
    Optional<QualityFailure> findOpenByBatchForUpdate(UUID batchId);

    /** Guarda causa, cantidad, observaciones, estado, el control que la originó y el descarte (si ya no está pendiente, apaga su reintento). */
    void save(QualityFailure failure);

    /** Guarda el descarte recién pedido; {@code nextAttemptAt} es la gracia antes de que el job pueda tomarlo. */
    void saveDiscardRequest(QualityFailure failure, Instant nextAttemptAt);

    /** Reclama con {@code FOR UPDATE SKIP LOCKED} los descartes pendientes vencidos y los arrienda. */
    List<ClaimedDiscard> claimPendingDiscards(Instant now, int limit, Duration lease);

    void recordFailedAttempt(UUID failureId, int attempts, Instant nextAttemptAt, String error);

    void reschedule(UUID failureId, Instant nextAttemptAt);
}

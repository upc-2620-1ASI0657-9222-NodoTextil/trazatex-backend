package com.nodotextil.trazatex.quality.application.port.out;

import com.nodotextil.trazatex.quality.domain.model.QualityControl;
import com.nodotextil.trazatex.sharedkernel.paging.PageRequest;
import com.nodotextil.trazatex.sharedkernel.paging.PageResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QualityControlRepositoryPort {

    /**
     * Guarda un control nuevo {@code STARTING}; {@code nextAttemptAt} es la gracia antes de que el job lo tome.
     *
     * @throws com.nodotextil.trazatex.sharedkernel.exception.ConflictException {@code CONTROL_ALREADY_ACTIVE}: el lote ya tiene un control
     *         activo (índice único; gana una de dos peticiones simultáneas)
     */
    void insertStarting(QualityControl control, Instant nextAttemptAt);

    Optional<QualityControl> findById(UUID controlId);

    /**
     * Una página de los controles de la empresa, del más reciente al más antiguo ({@code start_date} descendente y, a igual fecha, {@code id}).
     * Las pruebas de toda la página se leen con <b>una</b> consulta, no una por control.
     */
    PageResult<QualityControl> findPageByCompany(UUID companyId, PageRequest request);

    /** Con la fila bloqueada ({@code FOR UPDATE}): quien resuelve un control {@code STARTING} la toma antes de decidir. */
    Optional<QualityControl> findByIdForUpdate(UUID controlId);

    /** Guarda estado, resultado, fechas, motivo de cancelación y las pruebas nuevas de un control leído con {@link #findByIdForUpdate}. */
    void save(QualityControl control);

    /**
     * Reclama hasta {@code limit} controles {@code STARTING} con el intento vencido ({@code next_attempt_at <= now}) con
     * {@code FOR UPDATE SKIP LOCKED}: dos instancias del job nunca toman el mismo. Los marca con un arrendamiento de {@code lease}
     * ({@code next_attempt_at = now + lease}): si quien los reclamó cae antes de resolverlos, vencen y otro los repite.
     */
    List<ClaimedStart> claimPendingStarts(Instant now, int limit, Duration lease);

    /** Guarda la finalización recién pedida (el control sigue {@code IN_PROGRESS}); {@code nextAttemptAt} es la gracia antes de que el job pueda tomarla. */
    void saveFinishRequest(QualityControl control, Instant nextAttemptAt);

    /** Como {@link #claimPendingStarts} para los controles {@code IN_PROGRESS} con la finalización pendiente (el {@code RELEASE} sin respuesta). */
    List<ClaimedStart> claimPendingFinishes(Instant now, int limit, Duration lease);

    /** El comando (inicio o finalización) no tuvo respuesta: registra el intento y cuándo volver a intentar. */
    void recordFailedAttempt(UUID controlId, int attempts, Instant nextAttemptAt, String error);

    /** Vuelve a programar sin contar un intento (el circuit breaker estaba abierto y no se llamó). */
    void reschedule(UUID controlId, Instant nextAttemptAt);
}

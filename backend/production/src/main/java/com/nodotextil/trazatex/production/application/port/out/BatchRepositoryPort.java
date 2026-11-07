package com.nodotextil.trazatex.production.application.port.out;

import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.QrCode;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia de lotes. Debe usarse dentro de una transacción: el bloqueo optimista ({@code version}) y los bloqueos
 * de fila ({@code ...ForUpdate}) viven en ella.
 */
public interface BatchRepositoryPort {

    /**
     * Inserta o actualiza. Si otra transacción modificó el lote desde que se leyó, lanza el error de bloqueo optimista.
     *
     * @return la versión del lote ({@code @Version}) tras guardar: la que viaja en {@code BatchPhaseChanged.batchVersion}
     */
    long save(Batch batch);

    /** @return la versión de cada lote tras guardar, por id */
    default Map<UUID, Long> saveAll(Collection<Batch> batches) {
        Map<UUID, Long> versions = new LinkedHashMap<>();
        batches.forEach(batch -> versions.put(batch.id(), save(batch)));
        return versions;
    }

    Optional<Batch> findById(UUID id);

    /** Lectura con bloqueo de la fila hasta el fin de la transacción. */
    Optional<Batch> findByIdForUpdate(UUID id);

    /**
     * Varios lotes con bloqueo de fila, <b>siempre en orden de id</b>: dos transacciones que piden los mismos lotes
     * en distinto orden no se interbloquean. Devuelve los que existen, en ese orden.
     */
    List<Batch> findAllForUpdate(Collection<UUID> ids);

    /** Lectura sin bloqueo de varios lotes (detalle de una transferencia); sin orden garantizado, los que no existen se omiten. */
    List<Batch> findAllById(Collection<UUID> ids);

    Optional<Batch> findByQrCode(QrCode qrCode);

    Page<Batch> search(BatchSearchCriteria criteria);

    /** ¿Ya existe un lote con ese Traceability ID o QR? Es el predicado que usa {@code BatchFactory}. */
    boolean isCodeTaken(String code);
}

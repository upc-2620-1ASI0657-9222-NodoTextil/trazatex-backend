package com.nodotextil.trazatex.quality.application.port.in;

import com.nodotextil.trazatex.quality.application.dto.Actor;
import com.nodotextil.trazatex.quality.application.dto.StartControlResult;

import java.util.UUID;

/** US23 / US34: inicia un control sobre un lote de la propia empresa. */
public interface StartQualityControlUseCase {

    /**
     * @throws com.nodotextil.trazatex.sharedkernel.exception.NotFoundException {@code BATCH_NOT_FOUND}: el lote no existe, es de otra empresa
     *         o Production respondió 404
     * @throws com.nodotextil.trazatex.sharedkernel.exception.ConflictException  el lote no se puede controlar, ya tiene un control activo, o Production
     *         rechazó el inicio (el control queda {@code CANCELLED} con el código de Production como motivo)
     */
    StartControlResult start(UUID batchId, Actor actor, String internalObservations);
}

package com.nodotextil.trazatex.production.application.port.in;

/** El job de la cola diferida (ADR-007): sube las evidencias que no pudieron subirse durante la petición. */
public interface UploadPendingEvidenceUseCase {

    /** @return cuántas evidencias se procesaron (subidas o no) en esta pasada */
    int uploadDue();
}

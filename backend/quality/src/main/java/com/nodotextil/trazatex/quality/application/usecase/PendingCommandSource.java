package com.nodotextil.trazatex.quality.application.usecase;

/** Una de las cuatro fuentes de comandos pendientes a Production que reintenta el job. Cada una reclama y resuelve lo suyo. */
interface PendingCommandSource {

    enum Kind {
        /** Controles {@code STARTING}: {@code START_EVALUATION} sin respuesta. */
        START,
        /** Controles {@code IN_PROGRESS} con la finalización pedida: {@code RELEASE} sin respuesta. */
        FINISH,
        /** Fallas {@code OPEN} con el descarte pedido: {@code DISCARD} sin respuesta. */
        DISCARD,
        /** Avisos de falla derivada {@code PENDING}: {@code HOLD} por mandar o sin respuesta. */
        HOLD
    }

    Kind kind();

    /** Una pasada: reclama lo vencido y lo resuelve. @return cuántos reclamó */
    int recover();
}

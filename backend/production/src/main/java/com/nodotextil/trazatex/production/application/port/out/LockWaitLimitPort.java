package com.nodotextil.trazatex.production.application.port.out;

import java.time.Duration;

/**
 * Limita cuánto espera la transacción actual a un bloqueo de fila ({@code lock_timeout}): si una operación del usuario tiene
 * el lote con {@code FOR UPDATE}, el comando falla pronto con un error de bloqueo —antes de que Quality abandone la llamada— en
 * lugar de quedarse esperando.
 */
public interface LockWaitLimitPort {

    void limit(Duration wait);
}

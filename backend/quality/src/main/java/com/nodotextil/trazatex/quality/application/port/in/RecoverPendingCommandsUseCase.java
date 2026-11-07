package com.nodotextil.trazatex.quality.application.port.in;

/**
 * Reintenta los comandos a Production que no tuvieron respuesta. Cuatro fuentes, cada una con su propio manejo de fallos (una que falla o
 * no puede llamar a Production no frena a las otras): el inicio y la finalización de un control, el descarte de una falla y la retención
 * por falla derivada.
 */
public interface RecoverPendingCommandsUseCase {

    /** Inicio, finalización y descarte (job de 15 s). @return cuántos reclamó */
    int recoverPending();

    /** Retenciones por falla derivada (job de 5 s). @return cuántos reclamó */
    int recoverPendingHolds();

    /** Las cuatro fuentes. */
    int recoverAll();
}

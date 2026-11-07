package com.nodotextil.trazatex.quality.application.port.out;

/**
 * El comando síncrono Quality → Production (ADR-009), la única llamada REST entre microservicios. Con timeout, circuit breaker y un
 * retry acotado dentro del adaptador y <b>sin fallback que simule éxito</b>.
 */
public interface ProductionPhasePort {

    /** Nunca lanza por una respuesta de Production ni por una caída: lo dice el {@link PhaseCommandOutcome}. */
    PhaseCommandOutcome send(PhaseCommand command);
}

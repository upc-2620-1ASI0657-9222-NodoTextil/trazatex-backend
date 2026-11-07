package com.nodotextil.trazatex.production.domain.model;

/**
 * El balance de una transformación ya validado, tal como lo recibe una {@link TransformationStrategy}. Es inmutable y
 * llega después de comprobar la invariante: una estrategia puede <b>rechazar</b> (agregar restricciones) pero no
 * modificar ni relajar nada.
 */
public record BalanceSummary(
        Quantity totalInput,
        Quantity totalOutput,
        Quantity waste,
        Quantity shrinkage,
        int inputCount,
        int outputCount) {
}

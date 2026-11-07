package com.nodotextil.trazatex.production.domain.model;

/**
 * Strategy de ADR-003: validaciones propias de cada tipo de transformación. Solo agrega restricciones sobre un balance
 * que {@link Transformation#finalize} ya comprobó; nunca cambia la fórmula de la merma. Agregar un tipo (RNF-09) es
 * agregar un valor de {@link TransformationType} y una estrategia, sin tocar {@code Transformation}.
 */
public interface TransformationStrategy {

    TransformationType type();

    /** Lanza {@code ValidationException} (400) si el balance incumple una regla propia del tipo. */
    void validate(BalanceSummary balance);
}

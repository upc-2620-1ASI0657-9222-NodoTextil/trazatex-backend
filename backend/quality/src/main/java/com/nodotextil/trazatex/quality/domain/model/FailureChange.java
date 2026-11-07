package com.nodotextil.trazatex.quality.domain.model;

/** La acción sobre la falla y la falla resultante ({@code null} cuando la acción es {@link FailureAction#NONE}). */
public record FailureChange(FailureAction action, QualityFailure failure) {
}

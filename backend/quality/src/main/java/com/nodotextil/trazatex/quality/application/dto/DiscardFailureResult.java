package com.nodotextil.trazatex.quality.application.dto;

/** La falla tras pedir descartar el lote. {@code completed = true}: Production confirmó {@code DISCARD} y la falla está {@code CONFIRMED} (200); {@code false}: 202. */
public record DiscardFailureResult(FailureResult failure, boolean completed) {
}

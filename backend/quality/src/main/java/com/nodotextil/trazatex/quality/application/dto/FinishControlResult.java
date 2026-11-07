package com.nodotextil.trazatex.quality.application.dto;

/**
 * El control tras pedir finalizarlo. {@code completed = true}: Production confirmó {@code RELEASE} y el control está {@code FINISHED} (200);
 * {@code failure} es la que ese control generó, reabrió o resolvió ({@code null} si salió conforme sin falla que resolver). {@code false}: Production no
 * respondió; el control sigue {@code IN_PROGRESS} con {@code finishPending} y el job lo resuelve (202).
 */
public record FinishControlResult(ControlResult control, FailureResult failure, boolean completed) {
}

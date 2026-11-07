package com.nodotextil.trazatex.quality.application.dto;

/**
 * El control creado. {@code confirmed = true}: Production confirmó y el control ya está {@code IN_PROGRESS} (201). {@code false}: Production
 * no respondió y el control sigue {@code STARTING} mientras el job lo resuelve (202).
 */
public record StartControlResult(ControlResult control, boolean confirmed) {
}

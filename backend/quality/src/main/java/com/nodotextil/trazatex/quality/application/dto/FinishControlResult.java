package com.nodotextil.trazatex.quality.application.dto;

public record FinishControlResult(ControlResult control, FailureResult failure, boolean completed) {
}

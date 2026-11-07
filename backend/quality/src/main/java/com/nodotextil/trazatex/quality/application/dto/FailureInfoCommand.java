package com.nodotextil.trazatex.quality.application.dto;

import java.math.BigDecimal;

/** US26 esc. 1: causa, cantidad afectada (kg) y observaciones. */
public record FailureInfoCommand(String rootCause, BigDecimal affectedQtyKg, String observations) {
}

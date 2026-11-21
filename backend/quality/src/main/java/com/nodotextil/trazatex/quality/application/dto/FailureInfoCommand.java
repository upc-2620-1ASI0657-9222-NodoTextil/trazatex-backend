package com.nodotextil.trazatex.quality.application.dto;

import java.math.BigDecimal;

public record FailureInfoCommand(String rootCause, BigDecimal affectedQtyKg, String observations) {
}

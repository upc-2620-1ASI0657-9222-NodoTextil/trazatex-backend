package com.nodotextil.trazatex.production.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record SplitBatchCommand(List<Child> children) {

    public record Child(String name, BigDecimal quantityKg) {
    }
}

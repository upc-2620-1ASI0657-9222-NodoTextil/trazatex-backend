package com.nodotextil.trazatex.production.domain.model;

public record BalanceSummary(
        Quantity totalInput,
        Quantity totalOutput,
        Quantity waste,
        Quantity shrinkage,
        int inputCount,
        int outputCount) {
}

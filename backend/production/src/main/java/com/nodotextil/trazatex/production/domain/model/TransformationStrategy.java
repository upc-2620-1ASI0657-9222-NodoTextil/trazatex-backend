package com.nodotextil.trazatex.production.domain.model;

public interface TransformationStrategy {

    TransformationType type();

    void validate(BalanceSummary balance);
}

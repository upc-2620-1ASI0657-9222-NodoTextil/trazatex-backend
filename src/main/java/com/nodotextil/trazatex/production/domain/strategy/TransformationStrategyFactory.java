package com.nodotextil.trazatex.production.domain.strategy;

import com.nodotextil.trazatex.production.domain.InvalidTransformationException;
import com.nodotextil.trazatex.production.domain.TransformationType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TransformationStrategyFactory {

    private final Map<TransformationType, TransformationStrategy> strategies;

    public TransformationStrategyFactory(List<TransformationStrategy> strategies) {
        if (strategies == null || strategies.isEmpty()) {
            throw new IllegalArgumentException("At least one transformation strategy is required");
        }
        Map<TransformationType, TransformationStrategy> indexed =
                new EnumMap<>(TransformationType.class);
        for (TransformationStrategy strategy : strategies) {
            Objects.requireNonNull(strategy, "Transformation strategy is required");
            TransformationStrategy previous = indexed.put(strategy.type(), strategy);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicated transformation strategy for " + strategy.type());
            }
        }
        this.strategies = Map.copyOf(indexed);
    }

    public TransformationStrategy getStrategy(TransformationType type) {
        TransformationStrategy strategy = strategies.get(
                Objects.requireNonNull(type, "Transformation type is required"));
        if (strategy == null) {
            throw new InvalidTransformationException(
                    "No transformation strategy configured for " + type);
        }
        return strategy;
    }
}

package com.nodotextil.trazatex.production.domain.strategy;

import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.CompositionCalculator;
import com.nodotextil.trazatex.production.domain.CompositionComponent;
import com.nodotextil.trazatex.production.domain.TransformationType;
import java.util.List;

public final class FinishingTransformationStrategy implements TransformationStrategy {

    @Override
    public TransformationType type() {
        return TransformationType.FINISHING;
    }

    @Override
    public List<CompositionComponent> calculateOutputComposition(List<Batch> inputs) {
        return CompositionCalculator.weightedByQuantity(inputs);
    }
}

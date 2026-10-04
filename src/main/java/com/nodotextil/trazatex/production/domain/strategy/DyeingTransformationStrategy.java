package com.nodotextil.trazatex.production.domain.strategy;

import com.nodotextil.trazatex.production.domain.Batch;
import com.nodotextil.trazatex.production.domain.CompositionCalculator;
import com.nodotextil.trazatex.production.domain.CompositionComponent;
import com.nodotextil.trazatex.production.domain.TransformationType;
import java.util.List;

public final class DyeingTransformationStrategy implements TransformationStrategy {

    @Override
    public TransformationType type() {
        return TransformationType.DYEING;
    }

    @Override
    public List<CompositionComponent> calculateOutputComposition(List<Batch> inputs) {
        return CompositionCalculator.weightedByQuantity(inputs);
    }
}

package com.nodotextil.trazatex.production.domain.service;

import com.nodotextil.trazatex.production.domain.model.Batch;
import com.nodotextil.trazatex.production.domain.model.Machine;
import com.nodotextil.trazatex.production.domain.model.Transformation;
import com.nodotextil.trazatex.production.domain.model.TransformationSnapshot;
import com.nodotextil.trazatex.production.domain.model.TransformationStrategy;
import com.nodotextil.trazatex.production.domain.model.TransformationType;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Factory Method de ADR-003: elige la {@link TransformationStrategy} del tipo y crea o reconstruye la
 * {@link Transformation}. Exige una estrategia por cada {@link TransformationType} al construirse, así que un tipo nuevo
 * sin su estrategia falla al arrancar y no en producción (RNF-09).
 */
public final class TransformationFactory {

    private final Map<TransformationType, TransformationStrategy> strategies = new EnumMap<>(TransformationType.class);

    public TransformationFactory(List<? extends TransformationStrategy> strategies) {
        for (TransformationStrategy strategy : strategies) {
            if (this.strategies.put(strategy.type(), strategy) != null) {
                throw new IllegalArgumentException("Hay dos estrategias para " + strategy.type());
            }
        }
        for (TransformationType type : TransformationType.values()) {
            if (!this.strategies.containsKey(type)) {
                throw new IllegalArgumentException("Falta la estrategia de " + type);
            }
        }
    }

    public TransformationStrategy strategyFor(TransformationType type) {
        return strategies.get(type);
    }

    public Transformation start(UUID id, TransformationType type, UUID companyId, Machine machine, UUID operatorId,
                                List<Batch> batches, Instant now) {
        return Transformation.start(id, strategyFor(type), companyId, machine, operatorId, batches, now);
    }

    public Transformation restore(TransformationSnapshot snapshot) {
        return Transformation.restore(strategyFor(snapshot.type()), snapshot);
    }
}

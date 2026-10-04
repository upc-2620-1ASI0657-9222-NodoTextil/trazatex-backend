package com.nodotextil.trazatex.production.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CompositionCalculator {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int CALCULATION_SCALE = 10;

    private CompositionCalculator() {
    }

    public static List<CompositionComponent> weightedByQuantity(List<Batch> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new InvalidTransformationException(
                    "Composition requires at least one input batch");
        }
        if (inputs.size() == 1) {
            return inputs.getFirst().composition();
        }

        BigDecimal totalKg = inputs.stream()
                .map(Batch::quantityKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, BigDecimal> materialWeights = new LinkedHashMap<>();
        for (Batch input : inputs) {
            for (CompositionComponent component : input.composition()) {
                BigDecimal weighted = input.quantityKg().multiply(component.percentage());
                materialWeights.merge(component.material(), weighted, BigDecimal::add);
            }
        }

        List<CompositionComponent> result = new ArrayList<>();
        BigDecimal accumulated = BigDecimal.ZERO;
        int index = 0;
        for (Map.Entry<String, BigDecimal> entry : materialWeights.entrySet()) {
            index++;
            BigDecimal percentage;
            if (index == materialWeights.size()) {
                percentage = ONE_HUNDRED.subtract(accumulated);
            } else {
                percentage = entry.getValue()
                        .divide(totalKg, CALCULATION_SCALE, RoundingMode.HALF_UP);
                accumulated = accumulated.add(percentage);
            }
            result.add(new CompositionComponent(entry.getKey(), percentage));
        }
        return List.copyOf(result);
    }
}

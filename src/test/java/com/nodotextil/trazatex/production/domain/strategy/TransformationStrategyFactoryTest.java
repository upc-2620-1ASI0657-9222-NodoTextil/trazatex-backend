package com.nodotextil.trazatex.production.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.nodotextil.trazatex.production.domain.TransformationType;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransformationStrategyFactoryTest {

    @Test
    void selectsStrategyForEveryTransformationType() {
        TransformationStrategyFactory factory = new TransformationStrategyFactory(List.of(
                new SpinningTransformationStrategy(),
                new WeavingTransformationStrategy(),
                new DyeingTransformationStrategy(),
                new FinishingTransformationStrategy(),
                new CuttingTransformationStrategy(),
                new GarmentingTransformationStrategy()));

        assertThat(factory.getStrategy(TransformationType.SPINNING))
                .isInstanceOf(SpinningTransformationStrategy.class);
        assertThat(factory.getStrategy(TransformationType.WEAVING))
                .isInstanceOf(WeavingTransformationStrategy.class);
        assertThat(factory.getStrategy(TransformationType.DYEING))
                .isInstanceOf(DyeingTransformationStrategy.class);
        assertThat(factory.getStrategy(TransformationType.FINISHING))
                .isInstanceOf(FinishingTransformationStrategy.class);
        assertThat(factory.getStrategy(TransformationType.CUTTING))
                .isInstanceOf(CuttingTransformationStrategy.class);
        assertThat(factory.getStrategy(TransformationType.GARMENTING))
                .isInstanceOf(GarmentingTransformationStrategy.class);
    }
}

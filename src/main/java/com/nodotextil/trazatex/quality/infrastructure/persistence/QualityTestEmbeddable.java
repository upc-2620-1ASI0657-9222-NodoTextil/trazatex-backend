package com.nodotextil.trazatex.quality.infrastructure.persistence;

import com.nodotextil.trazatex.quality.domain.QualityTest;
import com.nodotextil.trazatex.quality.domain.TestResult;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.UUID;

@Embeddable
class QualityTestEmbeddable {

    private UUID id;
    private String criterion;
    private String expectedValue;
    private String actualValue;
    private String unit;

    @Enumerated(EnumType.STRING)
    private TestResult result;

    private String observations;

    protected QualityTestEmbeddable() {
    }

    static QualityTestEmbeddable fromDomain(QualityTest test) {
        QualityTestEmbeddable entity = new QualityTestEmbeddable();
        entity.id = test.id();
        entity.criterion = test.criterion();
        entity.expectedValue = test.expectedValue();
        entity.actualValue = test.actualValue();
        entity.unit = test.unit();
        entity.result = test.result();
        entity.observations = test.observations();
        return entity;
    }

    QualityTest toDomain() {
        return new QualityTest(
                id,
                criterion,
                expectedValue,
                actualValue,
                unit,
                result,
                observations
        );
    }
}

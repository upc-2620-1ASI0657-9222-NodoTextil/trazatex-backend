package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.domain.Transformation;

final class TransformationJpaMapper {

    private TransformationJpaMapper() {
    }

    static TransformationJpaEntity toEntity(Transformation transformation) {
        return new TransformationJpaEntity(
                transformation.id(),
                transformation.companyId(),
                transformation.operatorId(),
                transformation.machineId(),
                transformation.type(),
                transformation.inputBatchIds(),
                transformation.outputBatchIds(),
                transformation.startedAt(),
                transformation.completedAt());
    }

    static Transformation toDomain(TransformationJpaEntity entity) {
        return Transformation.reconstitute(
                entity.getId(),
                entity.getCompanyId(),
                entity.getOperatorId(),
                entity.getMachineId(),
                entity.getType(),
                entity.getInputBatchIds(),
                entity.getOutputBatchIds(),
                entity.getStartedAt(),
                entity.getCompletedAt());
    }
}

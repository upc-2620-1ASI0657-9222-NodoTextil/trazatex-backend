package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.domain.Transfer;

final class TransferJpaMapper {

    private TransferJpaMapper() {
    }

    static TransferJpaEntity toEntity(Transfer transfer) {
        return new TransferJpaEntity(
                transfer.id(),
                transfer.sourceCompanyId(),
                transfer.destinationCompanyId(),
                transfer.batchIds(),
                transfer.status(),
                transfer.startedAt(),
                transfer.completedAt(),
                transfer.rejectionReason());
    }

    static Transfer toDomain(TransferJpaEntity entity) {
        return Transfer.reconstitute(
                entity.getId(),
                entity.getSourceCompanyId(),
                entity.getDestinationCompanyId(),
                entity.getBatchIds(),
                entity.getStatus(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getRejectionReason());
    }
}

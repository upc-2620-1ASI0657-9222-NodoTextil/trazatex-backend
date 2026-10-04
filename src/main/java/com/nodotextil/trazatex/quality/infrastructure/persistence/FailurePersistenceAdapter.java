package com.nodotextil.trazatex.quality.infrastructure.persistence;

import com.nodotextil.trazatex.quality.application.port.FailureRepository;
import com.nodotextil.trazatex.quality.domain.Failure;
import com.nodotextil.trazatex.quality.domain.FailureStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FailurePersistenceAdapter implements FailureRepository {

    private final SpringDataFailureRepository repository;

    public FailurePersistenceAdapter(SpringDataFailureRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Failure> findById(UUID failureId) {
        return repository.findById(failureId).map(FailureJpaEntity::toDomain);
    }

    @Override
    public Optional<Failure> findActiveByBatchId(UUID batchId) {
        return repository.findFirstByBatchIdAndStatusIn(
                        batchId,
                        List.of(
                                FailureStatus.PENDING_DECISION,
                                FailureStatus.IN_REEVALUATION
                        )
                )
                .map(FailureJpaEntity::toDomain);
    }

    @Override
    public Failure save(Failure failure) {
        return repository.save(FailureJpaEntity.fromDomain(failure)).toDomain();
    }
}

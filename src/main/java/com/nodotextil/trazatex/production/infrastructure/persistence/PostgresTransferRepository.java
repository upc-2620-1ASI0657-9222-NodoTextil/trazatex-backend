package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.domain.Transfer;
import com.nodotextil.trazatex.production.domain.TransferRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PostgresTransferRepository implements TransferRepository {

    private final SpringDataTransferJpaRepository jpaRepository;

    PostgresTransferRepository(SpringDataTransferJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public Transfer save(Transfer transfer) {
        return TransferJpaMapper.toDomain(
                jpaRepository.save(TransferJpaMapper.toEntity(transfer)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Transfer> findById(UUID id) {
        return jpaRepository.findById(id).map(TransferJpaMapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<Transfer> findByIdForUpdate(UUID id) {
        return jpaRepository.findByIdForUpdate(id).map(TransferJpaMapper::toDomain);
    }
}

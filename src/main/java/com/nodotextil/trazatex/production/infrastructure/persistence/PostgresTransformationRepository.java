package com.nodotextil.trazatex.production.infrastructure.persistence;

import com.nodotextil.trazatex.production.domain.Transformation;
import com.nodotextil.trazatex.production.domain.TransformationRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PostgresTransformationRepository implements TransformationRepository {

    private final SpringDataTransformationJpaRepository jpaRepository;

    PostgresTransformationRepository(SpringDataTransformationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public Transformation save(Transformation transformation) {
        return TransformationJpaMapper.toDomain(
                jpaRepository.save(TransformationJpaMapper.toEntity(transformation)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Transformation> findById(UUID id) {
        return jpaRepository.findById(id).map(TransformationJpaMapper::toDomain);
    }
}

package com.nodotextil.trazatex.production.application.port.out;

import com.nodotextil.trazatex.production.domain.model.Transformation;

import java.util.Optional;
import java.util.UUID;

public interface TransformationRepositoryPort {

    void save(Transformation transformation);

    Optional<Transformation> findById(UUID id);

    Optional<Transformation> findByIdForUpdate(UUID id);

    Page<Transformation> search(TransformationSearchCriteria criteria);
}

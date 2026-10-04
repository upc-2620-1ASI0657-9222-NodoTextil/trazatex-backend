package com.nodotextil.trazatex.production.domain;

import java.util.Optional;
import java.util.UUID;

public interface TransformationRepository {

    Transformation save(Transformation transformation);

    Optional<Transformation> findById(UUID id);
}

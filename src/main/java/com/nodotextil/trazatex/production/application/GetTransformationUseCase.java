package com.nodotextil.trazatex.production.application;

import com.nodotextil.trazatex.production.domain.Transformation;
import com.nodotextil.trazatex.production.domain.TransformationRepository;
import java.util.UUID;

public class GetTransformationUseCase {

    private final TransformationRepository transformationRepository;

    public GetTransformationUseCase(TransformationRepository transformationRepository) {
        this.transformationRepository = transformationRepository;
    }

    public Transformation execute(UUID id) {
        return transformationRepository.findById(id)
                .orElseThrow(() -> new TransformationNotFoundException(id));
    }
}

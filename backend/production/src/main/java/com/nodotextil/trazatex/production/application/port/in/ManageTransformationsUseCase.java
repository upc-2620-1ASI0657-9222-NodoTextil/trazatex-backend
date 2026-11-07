package com.nodotextil.trazatex.production.application.port.in;

import com.nodotextil.trazatex.production.application.dto.Actor;
import com.nodotextil.trazatex.production.application.dto.CompleteTransformationCommand;
import com.nodotextil.trazatex.production.application.dto.CompleteTransformationResult;
import com.nodotextil.trazatex.production.application.dto.PageResult;
import com.nodotextil.trazatex.production.application.dto.StartTransformationCommand;
import com.nodotextil.trazatex.production.application.dto.TransformationResult;
import com.nodotextil.trazatex.production.domain.model.TransformationStatus;
import com.nodotextil.trazatex.production.domain.model.TransformationType;

import java.util.UUID;

/** US16 iniciar, US17–US19 finalizar con balance, US20 productos finales. */
public interface ManageTransformationsUseCase {

    TransformationResult start(StartTransformationCommand command, Actor actor);

    CompleteTransformationResult complete(UUID transformationId, CompleteTransformationCommand command, Actor actor);

    TransformationResult get(UUID transformationId, Actor actor);

    PageResult<TransformationResult> search(TransformationStatus status, TransformationType type, Integer page, Integer size, Actor actor);
}

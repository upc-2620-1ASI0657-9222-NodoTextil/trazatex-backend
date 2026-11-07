package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.application.dto.Actor;
import com.nodotextil.trazatex.production.application.dto.CompleteTransformationCommand;
import com.nodotextil.trazatex.production.application.dto.CompleteTransformationResult;
import com.nodotextil.trazatex.production.application.dto.PageResult;
import com.nodotextil.trazatex.production.application.dto.Role;
import com.nodotextil.trazatex.production.application.dto.StartTransformationCommand;
import com.nodotextil.trazatex.production.application.dto.TransformationResult;
import com.nodotextil.trazatex.production.application.port.in.ManageTransformationsUseCase;
import com.nodotextil.trazatex.production.domain.model.TransformationStatus;
import com.nodotextil.trazatex.production.domain.model.TransformationType;
import com.nodotextil.trazatex.production.infrastructure.web.security.RequiresRole;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/transformations")
class TransformationController {

    private final ManageTransformationsUseCase transformations;

    TransformationController(ManageTransformationsUseCase transformations) {
        this.transformations = transformations;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresRole(Role.OPERATOR)
    TransformationResult start(@RequestBody StartTransformationCommand request, Actor actor) {
        return transformations.start(request, actor);
    }

    @GetMapping
    @RequiresRole({Role.OPERATOR, Role.COMPANY_ADMIN})
    PageResult<TransformationResult> search(@RequestParam(required = false) TransformationStatus status,
                                            @RequestParam(required = false) TransformationType type,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size, Actor actor) {
        return transformations.search(status, type, page, size, actor);
    }

    @GetMapping("/{id}")
    @RequiresRole({Role.OPERATOR, Role.COMPANY_ADMIN})
    TransformationResult get(@PathVariable UUID id, Actor actor) {
        return transformations.get(id, actor);
    }

    /** US18/US19. La merma nunca se envía: el servicio la calcula (entrada − salida − desperdicio). */
    @PostMapping("/{id}/complete")
    @RequiresRole(Role.OPERATOR)
    CompleteTransformationResult complete(@PathVariable UUID id, @RequestBody CompleteTransformationCommand request, Actor actor) {
        return transformations.complete(id, request, actor);
    }
}

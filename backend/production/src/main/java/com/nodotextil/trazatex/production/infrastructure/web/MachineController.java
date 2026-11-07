package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.application.dto.Actor;
import com.nodotextil.trazatex.production.application.dto.MachineDetailResult;
import com.nodotextil.trazatex.production.application.dto.MachineResult;
import com.nodotextil.trazatex.production.application.dto.RegisterMachineCommand;
import com.nodotextil.trazatex.production.application.dto.Role;
import com.nodotextil.trazatex.production.application.port.in.ManageMachinesUseCase;
import com.nodotextil.trazatex.production.domain.model.MachineStatus;
import com.nodotextil.trazatex.production.infrastructure.web.security.RequiresRole;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/machines")
class MachineController {

    private final ManageMachinesUseCase machines;

    MachineController(ManageMachinesUseCase machines) {
        this.machines = machines;
    }

    record ChangeStatusRequest(MachineStatus status) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresRole(Role.COMPANY_ADMIN)
    MachineResult register(@RequestBody RegisterMachineCommand request, Actor actor) {
        return machines.register(request, actor);
    }

    @GetMapping
    @RequiresRole({Role.OPERATOR, Role.COMPANY_ADMIN})
    List<MachineResult> list(@RequestParam(required = false) MachineStatus status, Actor actor) {
        return machines.list(status, actor);
    }

    @GetMapping("/{id}")
    @RequiresRole({Role.OPERATOR, Role.COMPANY_ADMIN})
    MachineDetailResult get(@PathVariable UUID id, Actor actor) {
        return machines.get(id, actor);
    }

    @PatchMapping("/{id}/status")
    @RequiresRole(Role.COMPANY_ADMIN)
    MachineResult changeStatus(@PathVariable UUID id, @RequestBody ChangeStatusRequest request, Actor actor) {
        return machines.changeStatus(id, request.status(), actor);
    }
}

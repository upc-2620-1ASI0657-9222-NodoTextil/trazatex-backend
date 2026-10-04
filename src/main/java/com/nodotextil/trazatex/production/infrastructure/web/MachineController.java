package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.application.ChangeMachineStatusUseCase;
import com.nodotextil.trazatex.production.application.GetMachineUseCase;
import com.nodotextil.trazatex.production.application.RegisterMachineUseCase;
import com.nodotextil.trazatex.production.domain.Machine;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/machines")
public class MachineController {

    private final RegisterMachineUseCase registerMachineUseCase;
    private final GetMachineUseCase getMachineUseCase;
    private final ChangeMachineStatusUseCase changeMachineStatusUseCase;

    public MachineController(
            RegisterMachineUseCase registerMachineUseCase,
            GetMachineUseCase getMachineUseCase,
            ChangeMachineStatusUseCase changeMachineStatusUseCase) {
        this.registerMachineUseCase = registerMachineUseCase;
        this.getMachineUseCase = getMachineUseCase;
        this.changeMachineStatusUseCase = changeMachineStatusUseCase;
    }

    @PostMapping
    public ResponseEntity<MachineResponse> register(
            @Valid @RequestBody RegisterMachineRequest request) {
        Machine machine = registerMachineUseCase.execute(new RegisterMachineUseCase.Command(
                request.internalCode(),
                request.name(),
                request.type(),
                request.companyId()));
        return ResponseEntity
                .created(URI.create("/api/machines/" + machine.id()))
                .body(MachineResponse.from(machine));
    }

    @GetMapping("/{id}")
    public MachineResponse getById(@PathVariable UUID id) {
        return MachineResponse.from(getMachineUseCase.execute(id));
    }

    @PatchMapping("/{id}/status")
    public MachineResponse changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeMachineStatusRequest request) {
        return MachineResponse.from(changeMachineStatusUseCase.execute(id, request.status()));
    }
}

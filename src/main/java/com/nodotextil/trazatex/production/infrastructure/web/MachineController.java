package com.nodotextil.trazatex.production.infrastructure.web;

import com.nodotextil.trazatex.production.application.ChangeMachineStatusUseCase;
import com.nodotextil.trazatex.production.application.GetMachineUseCase;
import com.nodotextil.trazatex.production.application.RegisterMachineUseCase;
import com.nodotextil.trazatex.production.domain.InvalidMachineException;
import com.nodotextil.trazatex.production.domain.Machine;
import com.nodotextil.trazatex.shared.audit.AuditService;
import com.nodotextil.trazatex.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/machines")
@PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'OPERATOR')")
public class MachineController {

    private final RegisterMachineUseCase registerMachine;
    private final GetMachineUseCase getMachine;
    private final ChangeMachineStatusUseCase changeMachineStatus;
    private final AuditService auditService;

    public MachineController(
            RegisterMachineUseCase registerMachine,
            GetMachineUseCase getMachine,
            ChangeMachineStatusUseCase changeMachineStatus,
            AuditService auditService) {
        this.registerMachine = registerMachine;
        this.getMachine = getMachine;
        this.changeMachineStatus = changeMachineStatus;
        this.auditService = auditService;
    }

    @PostMapping
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResponseEntity<MachineResponse> register(
            @Valid @RequestBody RegisterMachineRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        Machine machine = registerMachine.execute(new RegisterMachineUseCase.Command(
                request.internalCode(), request.name(), request.type(), user.requireCompanyId()));
        auditService.record(user, "MACHINE_REGISTERED", "MACHINE", machine.id(), machine.internalCode());
        return ResponseEntity.created(URI.create("/api/machines/" + machine.id()))
                .body(MachineResponse.from(machine));
    }

    @GetMapping("/{id}")
    public MachineResponse getById(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        Machine machine = getMachine.execute(id);
        requireCompany(machine, user.requireCompanyId());
        return MachineResponse.from(machine);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public MachineResponse changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeMachineStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        Machine machine = changeMachineStatus.execute(id, user.requireCompanyId(), request.status());
        auditService.record(user, "MACHINE_STATUS_CHANGED", "MACHINE", id, request.status().name());
        return MachineResponse.from(machine);
    }

    private static void requireCompany(Machine machine, UUID companyId) {
        if (!machine.companyId().equals(companyId)) {
            throw new InvalidMachineException("Machine belongs to another company");
        }
    }
}

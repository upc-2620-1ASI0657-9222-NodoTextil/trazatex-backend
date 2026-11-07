package com.nodotextil.trazatex.organizationaccess.infrastructure.web;

import com.nodotextil.trazatex.organizationaccess.application.dto.Actor;
import com.nodotextil.trazatex.organizationaccess.application.dto.AdminRequestResult;
import com.nodotextil.trazatex.organizationaccess.application.dto.RequestAdminCommand;
import com.nodotextil.trazatex.organizationaccess.application.port.in.ManageAdminRequestsUseCase;
import com.nodotextil.trazatex.organizationaccess.domain.model.AdminRequestStatus;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import com.nodotextil.trazatex.organizationaccess.infrastructure.web.security.RequiresRole;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin-requests")
class AdminRequestController {

    private final ManageAdminRequestsUseCase requests;

    AdminRequestController(ManageAdminRequestsUseCase requests) {
        this.requests = requests;
    }

    record RequestAdminRequest(String fullName, String email, String position) {
    }

    record DecisionRequest(AdminRequestStatus decision) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresRole(Role.COMPANY_ADMIN)
    AdminRequestResult request(@RequestBody RequestAdminRequest request, Actor actor) {
        return requests.request(new RequestAdminCommand(request.fullName(), request.email(), request.position()), actor);
    }

    @GetMapping
    @RequiresRole({Role.LICENSE_OWNER, Role.COMPANY_ADMIN})
    List<AdminRequestResult> list(Actor actor) {
        return requests.list(actor);
    }

    @PatchMapping("/{id}/decision")
    @RequiresRole(Role.LICENSE_OWNER)
    AdminRequestResult decide(@PathVariable UUID id, @RequestBody DecisionRequest request, Actor actor) {
        return requests.decide(id, request.decision(), actor);
    }
}

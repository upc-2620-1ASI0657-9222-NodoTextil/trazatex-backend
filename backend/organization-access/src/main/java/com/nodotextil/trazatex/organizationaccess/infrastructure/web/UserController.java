package com.nodotextil.trazatex.organizationaccess.infrastructure.web;

import com.nodotextil.trazatex.organizationaccess.application.dto.Actor;
import com.nodotextil.trazatex.organizationaccess.application.dto.UserResult;
import com.nodotextil.trazatex.organizationaccess.application.port.in.ManageUsersUseCase;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import com.nodotextil.trazatex.organizationaccess.domain.model.UserStatus;
import com.nodotextil.trazatex.organizationaccess.infrastructure.web.security.RequiresRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
class UserController {

    private final ManageUsersUseCase users;

    UserController(ManageUsersUseCase users) {
        this.users = users;
    }

    record ChangeStatusRequest(UserStatus status) {
    }

    @GetMapping
    @RequiresRole(Role.COMPANY_ADMIN)
    List<UserResult> list(Actor actor) {
        return users.list(actor);
    }

    @PatchMapping("/{id}/status")
    @RequiresRole(Role.COMPANY_ADMIN)
    UserResult changeStatus(@PathVariable UUID id, @RequestBody ChangeStatusRequest request, Actor actor) {
        return users.changeStatus(id, request.status(), actor);
    }
}

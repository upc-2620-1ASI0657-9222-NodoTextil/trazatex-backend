package com.nodotextil.trazatex.organizationaccess.infrastructure.web;

import com.nodotextil.trazatex.organizationaccess.application.dto.Actor;
import com.nodotextil.trazatex.organizationaccess.application.dto.CompanyCapacityResult;
import com.nodotextil.trazatex.organizationaccess.application.port.in.ConsultCompanyCapacityUseCase;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import com.nodotextil.trazatex.organizationaccess.infrastructure.web.security.RequiresRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** El cupo de usuarios de la propia empresa ({@code {limit, activeUsers, pendingInvitations}}). Solo llama al caso de uso (regla 5 de CLAUDE.md). */
@RestController
class CompanyCapacityController {

    private final ConsultCompanyCapacityUseCase capacity;

    CompanyCapacityController(ConsultCompanyCapacityUseCase capacity) {
        this.capacity = capacity;
    }

    @GetMapping("/api/companies/current/capacity")
    @RequiresRole(Role.COMPANY_ADMIN)
    CompanyCapacityResult get(Actor actor) {
        return capacity.capacity(actor.companyId());
    }
}

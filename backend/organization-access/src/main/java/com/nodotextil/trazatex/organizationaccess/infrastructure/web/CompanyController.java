package com.nodotextil.trazatex.organizationaccess.infrastructure.web;

import com.nodotextil.trazatex.organizationaccess.application.dto.Actor;
import com.nodotextil.trazatex.organizationaccess.application.dto.CompanyResult;
import com.nodotextil.trazatex.organizationaccess.application.dto.CreateCompanyCommand;
import com.nodotextil.trazatex.organizationaccess.application.dto.CreateCompanyResult;
import com.nodotextil.trazatex.organizationaccess.application.port.in.ManageCompaniesUseCase;
import com.nodotextil.trazatex.organizationaccess.domain.model.CompanyStatus;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import com.nodotextil.trazatex.organizationaccess.domain.model.TextileActivity;
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
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/companies")
class CompanyController {

    private final ManageCompaniesUseCase companies;

    CompanyController(ManageCompaniesUseCase companies) {
        this.companies = companies;
    }

    record CreateCompanyRequest(String ruc, String businessName, Set<TextileActivity> activities, String adminEmail) {
    }

    record ChangeStatusRequest(CompanyStatus status) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresRole(Role.LICENSE_OWNER)
    CreateCompanyResult create(@RequestBody CreateCompanyRequest request, Actor actor) {
        return companies.create(new CreateCompanyCommand(request.ruc(), request.businessName(), request.activities(),
                request.adminEmail()), actor);
    }

    @GetMapping
    @RequiresRole(Role.LICENSE_OWNER)
    List<CompanyResult> list() {
        return companies.list();
    }

    @PatchMapping("/{id}/status")
    @RequiresRole(Role.LICENSE_OWNER)
    CompanyResult changeStatus(@PathVariable UUID id, @RequestBody ChangeStatusRequest request, Actor actor) {
        return companies.changeStatus(id, request.status(), actor);
    }
}

package com.nodotextil.trazatex.organizationaccess.application.port.in;

import com.nodotextil.trazatex.organizationaccess.application.dto.Actor;
import com.nodotextil.trazatex.organizationaccess.application.dto.CompanyResult;
import com.nodotextil.trazatex.organizationaccess.application.dto.CreateCompanyCommand;
import com.nodotextil.trazatex.organizationaccess.application.dto.CreateCompanyResult;
import com.nodotextil.trazatex.organizationaccess.domain.model.CompanyStatus;

import java.util.List;
import java.util.UUID;

public interface ManageCompaniesUseCase {

    /** US02: valida límite de empresas y RUC, crea la empresa y la invitación de su primer administrador. */
    CreateCompanyResult create(CreateCompanyCommand command, Actor actor);

    List<CompanyResult> list();

    /** US03. */
    CompanyResult changeStatus(UUID companyId, CompanyStatus status, Actor actor);
}

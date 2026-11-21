package com.nodotextil.trazatex.organizationaccess.application.port.in;

import com.nodotextil.trazatex.organizationaccess.application.dto.Actor;
import com.nodotextil.trazatex.organizationaccess.application.dto.CompanyResult;
import com.nodotextil.trazatex.organizationaccess.application.dto.CreateCompanyCommand;
import com.nodotextil.trazatex.organizationaccess.application.dto.CreateCompanyResult;
import com.nodotextil.trazatex.organizationaccess.domain.model.CompanyStatus;

import java.util.List;
import java.util.UUID;

public interface ManageCompaniesUseCase {

    CreateCompanyResult create(CreateCompanyCommand command, Actor actor);

    List<CompanyResult> list();

    CompanyResult changeStatus(UUID companyId, CompanyStatus status, Actor actor);
}

package com.nodotextil.trazatex.organizationaccess.application.port.in;

import com.nodotextil.trazatex.organizationaccess.application.dto.CompanyCapacityResult;

import java.util.UUID;

public interface ConsultCompanyCapacityUseCase {

    CompanyCapacityResult capacity(UUID companyId);
}

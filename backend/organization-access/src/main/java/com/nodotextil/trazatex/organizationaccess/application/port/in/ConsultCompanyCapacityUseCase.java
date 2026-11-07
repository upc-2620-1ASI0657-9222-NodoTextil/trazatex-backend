package com.nodotextil.trazatex.organizationaccess.application.port.in;

import com.nodotextil.trazatex.organizationaccess.application.dto.CompanyCapacityResult;

import java.util.UUID;

public interface ConsultCompanyCapacityUseCase {

    /**
     * El cupo de usuarios de la empresa de quien consulta.
     *
     * @throws com.nodotextil.trazatex.sharedkernel.exception.NotFoundException {@code LICENSE_NOT_FOUND} si la licencia no está inicializada
     */
    CompanyCapacityResult capacity(UUID companyId);
}

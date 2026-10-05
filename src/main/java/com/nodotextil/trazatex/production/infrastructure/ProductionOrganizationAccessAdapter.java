package com.nodotextil.trazatex.production.infrastructure;

import com.nodotextil.trazatex.organizationaccess.application.contract.OrganizationAccess;
import com.nodotextil.trazatex.production.application.port.OrganizationAccessPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ProductionOrganizationAccessAdapter implements OrganizationAccessPort {

    private final OrganizationAccess organizationAccess;

    ProductionOrganizationAccessAdapter(OrganizationAccess organizationAccess) {
        this.organizationAccess = organizationAccess;
    }

    @Override
    public boolean isCompanyActive(UUID companyId) {
        return organizationAccess.isCompanyActive(companyId);
    }

    @Override
    public boolean shareSameLicense(UUID sourceCompanyId, UUID destinationCompanyId) {
        return organizationAccess.companiesShareLicense(sourceCompanyId, destinationCompanyId);
    }
}

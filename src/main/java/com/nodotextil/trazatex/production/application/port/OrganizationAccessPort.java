package com.nodotextil.trazatex.production.application.port;

import java.util.UUID;

public interface OrganizationAccessPort {

    boolean isCompanyActive(UUID companyId);

    boolean shareSameLicense(UUID sourceCompanyId, UUID destinationCompanyId);
}

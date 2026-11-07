package com.nodotextil.trazatex.organizationaccess.application.port.in;

import com.nodotextil.trazatex.organizationaccess.application.dto.LicenseResult;

public interface ConsultLicenseUseCase {

    /** US01. */
    LicenseResult getLicense();
}

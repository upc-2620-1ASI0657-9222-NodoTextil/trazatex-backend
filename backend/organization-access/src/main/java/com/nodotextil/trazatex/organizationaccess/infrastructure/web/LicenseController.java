package com.nodotextil.trazatex.organizationaccess.infrastructure.web;

import com.nodotextil.trazatex.organizationaccess.application.dto.LicenseResult;
import com.nodotextil.trazatex.organizationaccess.application.port.in.ConsultLicenseUseCase;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import com.nodotextil.trazatex.organizationaccess.infrastructure.web.security.RequiresRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/license")
class LicenseController {

    private final ConsultLicenseUseCase license;

    LicenseController(ConsultLicenseUseCase license) {
        this.license = license;
    }

    @GetMapping
    @RequiresRole(Role.LICENSE_OWNER)
    LicenseResult get() {
        return license.getLicense();
    }
}

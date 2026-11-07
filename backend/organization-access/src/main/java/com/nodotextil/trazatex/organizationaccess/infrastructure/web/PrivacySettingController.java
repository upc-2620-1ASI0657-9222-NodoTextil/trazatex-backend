package com.nodotextil.trazatex.organizationaccess.infrastructure.web;

import com.nodotextil.trazatex.organizationaccess.application.dto.Actor;
import com.nodotextil.trazatex.organizationaccess.application.dto.PrivacyResult;
import com.nodotextil.trazatex.organizationaccess.application.port.in.ManagePrivacyUseCase;
import com.nodotextil.trazatex.organizationaccess.domain.model.Role;
import com.nodotextil.trazatex.organizationaccess.domain.model.Visibility;
import com.nodotextil.trazatex.organizationaccess.infrastructure.web.security.RequiresRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/privacy-settings")
class PrivacySettingController {

    private final ManagePrivacyUseCase privacy;

    PrivacySettingController(ManagePrivacyUseCase privacy) {
        this.privacy = privacy;
    }

    /** {@code fields}: nombre de campo configurable → SHARED | PRIVATE. Cambios parciales. */
    record UpdatePrivacyRequest(Map<String, Visibility> fields) {
    }

    @GetMapping
    @RequiresRole(Role.COMPANY_ADMIN)
    PrivacyResult get(Actor actor) {
        return privacy.get(actor);
    }

    @PutMapping
    @RequiresRole(Role.COMPANY_ADMIN)
    PrivacyResult update(@RequestBody UpdatePrivacyRequest request, Actor actor) {
        return privacy.update(request.fields(), actor);
    }
}

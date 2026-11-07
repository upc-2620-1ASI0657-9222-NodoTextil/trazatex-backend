package com.nodotextil.trazatex.organizationaccess.application.port.in;

import com.nodotextil.trazatex.organizationaccess.application.dto.Actor;
import com.nodotextil.trazatex.organizationaccess.application.dto.PrivacyResult;
import com.nodotextil.trazatex.organizationaccess.domain.model.Visibility;

import java.util.Map;

public interface ManagePrivacyUseCase {

    PrivacyResult get(Actor actor);

    /** US11. Cambios parciales por nombre de campo; todo o nada. */
    PrivacyResult update(Map<String, Visibility> changes, Actor actor);
}

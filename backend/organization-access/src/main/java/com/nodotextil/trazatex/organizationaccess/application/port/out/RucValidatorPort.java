package com.nodotextil.trazatex.organizationaccess.application.port.out;

import com.nodotextil.trazatex.organizationaccess.domain.model.Ruc;

public interface RucValidatorPort {

    void ensureActiveAndHabido(Ruc ruc);
}

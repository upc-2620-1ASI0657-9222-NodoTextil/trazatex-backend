package com.nodotextil.trazatex.organizationaccess.application.port.out;

import com.nodotextil.trazatex.organizationaccess.application.dto.CachedRucValidation;
import com.nodotextil.trazatex.organizationaccess.domain.model.Ruc;

import java.util.Optional;

public interface RucValidationCachePort {

    Optional<CachedRucValidation> find(Ruc ruc);

    void save(CachedRucValidation validation);

    void delete(Ruc ruc);
}

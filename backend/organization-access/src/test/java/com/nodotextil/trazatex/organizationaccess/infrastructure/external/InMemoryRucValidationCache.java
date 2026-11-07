package com.nodotextil.trazatex.organizationaccess.infrastructure.external;

import com.nodotextil.trazatex.organizationaccess.application.dto.CachedRucValidation;
import com.nodotextil.trazatex.organizationaccess.application.port.out.RucValidationCachePort;
import com.nodotextil.trazatex.organizationaccess.domain.model.Ruc;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class InMemoryRucValidationCache implements RucValidationCachePort {

    final Map<String, CachedRucValidation> entries = new HashMap<>();

    @Override
    public Optional<CachedRucValidation> find(Ruc ruc) {
        return Optional.ofNullable(entries.get(ruc.value()));
    }

    @Override
    public void save(CachedRucValidation validation) {
        entries.put(validation.ruc().value(), validation);
    }

    @Override
    public void delete(Ruc ruc) {
        entries.remove(ruc.value());
    }
}

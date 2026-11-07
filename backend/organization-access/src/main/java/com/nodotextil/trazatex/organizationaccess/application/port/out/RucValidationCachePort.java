package com.nodotextil.trazatex.organizationaccess.application.port.out;

import com.nodotextil.trazatex.organizationaccess.application.dto.CachedRucValidation;
import com.nodotextil.trazatex.organizationaccess.domain.model.Ruc;

import java.util.Optional;

/** {@code validated_ruc_cache}: solo guarda los RUC que Chequea.pe confirmó ACTIVO y HABIDO. */
public interface RucValidationCachePort {

    Optional<CachedRucValidation> find(Ruc ruc);

    /** Inserta o actualiza. */
    void save(CachedRucValidation validation);

    void delete(Ruc ruc);
}

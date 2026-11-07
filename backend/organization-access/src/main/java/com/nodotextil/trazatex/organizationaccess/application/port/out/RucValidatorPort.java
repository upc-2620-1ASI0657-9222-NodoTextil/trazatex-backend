package com.nodotextil.trazatex.organizationaccess.application.port.out;

import com.nodotextil.trazatex.organizationaccess.domain.model.Ruc;

/** ADR-012: caché y luego Chequea.pe. Solo se acepta un contribuyente ACTIVO y HABIDO; nunca un RUC sin validar. */
public interface RucValidatorPort {

    /**
     * @throws com.nodotextil.trazatex.sharedkernel.exception.ValidationException con {@code RUC_INVALID} si no es ACTIVO y HABIDO (o no existe)
     * @throws com.nodotextil.trazatex.organizationaccess.application.exception.ExternalServiceUnavailableException si no se pudo validar ni hay caché
     */
    void ensureActiveAndHabido(Ruc ruc);
}

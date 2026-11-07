package com.nodotextil.trazatex.organizationaccess.application.port.in;

public interface MaintainLoginAttemptsUseCase {

    /** Borra los registros vencidos de {@code login_attempts} (lo invoca el job). Devuelve cuántos. */
    int purgeExpired();
}

package com.nodotextil.trazatex.organizationaccess.application.port.in;

public interface MaintainLoginAttemptsUseCase {

    int purgeExpired();
}

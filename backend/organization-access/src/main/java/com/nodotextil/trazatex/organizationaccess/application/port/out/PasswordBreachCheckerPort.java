package com.nodotextil.trazatex.organizationaccess.application.port.out;

/** ADR-007: Have I Been Pwned por k-anonymity; si el servicio no responde se acepta y se audita. */
public interface PasswordBreachCheckerPort {

    enum Result { CLEAN, COMPROMISED, UNAVAILABLE }

    Result check(String rawPassword);
}

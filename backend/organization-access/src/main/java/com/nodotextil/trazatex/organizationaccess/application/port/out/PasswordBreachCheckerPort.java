package com.nodotextil.trazatex.organizationaccess.application.port.out;

public interface PasswordBreachCheckerPort {

    enum Result { CLEAN, COMPROMISED, UNAVAILABLE }

    Result check(String rawPassword);
}

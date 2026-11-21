package com.nodotextil.trazatex.organizationaccess.application.exception;

import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;

import java.time.Instant;

public class AccountLockedException extends TrazatexException {

    public static final String CODE = "ACCOUNT_LOCKED";

    private final Instant lockedUntil;

    public AccountLockedException(Instant lockedUntil) {
        super(CODE, "La cuenta está bloqueada temporalmente por intentos fallidos. Inténtalo de nuevo más tarde.");
        this.lockedUntil = lockedUntil;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }
}

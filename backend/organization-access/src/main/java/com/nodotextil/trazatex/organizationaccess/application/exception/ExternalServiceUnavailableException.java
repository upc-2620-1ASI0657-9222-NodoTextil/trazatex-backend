package com.nodotextil.trazatex.organizationaccess.application.exception;

import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;

/** Un sistema externo del que depende la operación no respondió y no hay un respaldo válido. Se mapea a 503. */
public class ExternalServiceUnavailableException extends TrazatexException {

    public ExternalServiceUnavailableException(String code, String message) {
        super(code, message);
    }
}

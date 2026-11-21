package com.nodotextil.trazatex.organizationaccess.application.exception;

import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;

public class ExternalServiceUnavailableException extends TrazatexException {

    public ExternalServiceUnavailableException(String code, String message) {
        super(code, message);
    }
}

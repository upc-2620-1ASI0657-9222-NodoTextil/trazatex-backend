package com.nodotextil.trazatex.quality.domain.exception;

import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;

import java.util.List;

public class FileTooLargeException extends TrazatexException {

    public FileTooLargeException(String message, List<String> details) {
        super(ErrorCodes.FILE_TOO_LARGE, message, details);
    }
}

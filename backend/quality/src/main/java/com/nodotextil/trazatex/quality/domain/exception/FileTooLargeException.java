package com.nodotextil.trazatex.quality.domain.exception;

import com.nodotextil.trazatex.sharedkernel.exception.TrazatexException;

import java.util.List;

/** Un archivo supera el máximo permitido. Se responde 413 (no es un 400: el cuerpo está bien formado, solo pesa demasiado). */
public class FileTooLargeException extends TrazatexException {

    public FileTooLargeException(String message, List<String> details) {
        super(ErrorCodes.FILE_TOO_LARGE, message, details);
    }
}

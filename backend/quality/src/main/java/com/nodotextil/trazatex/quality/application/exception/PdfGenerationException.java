package com.nodotextil.trazatex.quality.application.exception;

/** El reporte no se pudo generar (error del generador, tiempo agotado o demasiadas generaciones a la vez). Reintentar es seguro: la generación no tiene estado. */
public class PdfGenerationException extends RuntimeException {

    public PdfGenerationException(String message) {
        super(message);
    }

    public PdfGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}

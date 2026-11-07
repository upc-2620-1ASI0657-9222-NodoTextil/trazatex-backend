package com.nodotextil.trazatex.traceability.domain.exception;

/** Códigos estables de los errores de Traceability. */
public final class ErrorCodes {

    /** Un evento llegó sin un dato obligatorio (o con uno inválido): reintentar no lo arregla, va a la DLT. */
    public static final String INVALID_EVENT = "INVALID_EVENT";

    /** El lote no existe, o está incompleto (provisional): no se distingue para no revelar que existe. */
    public static final String BATCH_NOT_FOUND = "BATCH_NOT_FOUND";
    /** Una búsqueda sin QR ni Traceability ID. */
    public static final String MISSING_SEARCH_CRITERIA = "MISSING_SEARCH_CRITERIA";
    public static final String INVALID_PAGINATION = "INVALID_PAGINATION";

    private ErrorCodes() {
    }
}

package com.nodotextil.trazatex.production.application.dto;

/** {@code access}: {@code OWN} (lote de la empresa, todos sus datos) o {@code SHARED} (lote ajeno, solo campos siempre compartidos). */
public record QrLookupResult(String access, QrView batch) {

    public static final String OWN = "OWN";
    public static final String SHARED = "SHARED";
}

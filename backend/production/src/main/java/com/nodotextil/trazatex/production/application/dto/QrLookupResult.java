package com.nodotextil.trazatex.production.application.dto;

public record QrLookupResult(String access, QrView batch) {

    public static final String OWN = "OWN";
    public static final String SHARED = "SHARED";
}

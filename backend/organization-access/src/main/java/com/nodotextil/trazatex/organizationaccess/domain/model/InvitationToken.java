package com.nodotextil.trazatex.organizationaccess.domain.model;

import java.security.SecureRandom;
import java.util.Base64;

/** Token aleatorio e impredecible del enlace de invitación: 256 bits de {@link SecureRandom} en Base64 URL (43 caracteres). */
public record InvitationToken(String value) {

    private static final int BYTES = 32;
    private static final int MIN_LENGTH = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    public InvitationToken {
        if (value == null || value.length() < MIN_LENGTH || value.length() > 128) {
            throw new IllegalArgumentException("Token de invitación inválido");
        }
    }

    public static InvitationToken generate() {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        return new InvitationToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    /** No muestra el valor para que el token no termine en los logs. */
    @Override
    public String toString() {
        return "InvitationToken[****]";
    }
}

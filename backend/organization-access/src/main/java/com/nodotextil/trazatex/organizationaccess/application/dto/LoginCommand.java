package com.nodotextil.trazatex.organizationaccess.application.dto;

public record LoginCommand(String email, String password) {

    /** La contraseña nunca se imprime (logs, trazas de excepciones). */
    @Override
    public String toString() {
        return "LoginCommand[email=" + email + ", password=****]";
    }
}

package com.nodotextil.trazatex.organizationaccess.application.dto;

public record LoginCommand(String email, String password) {

    @Override
    public String toString() {
        return "LoginCommand[email=" + email + ", password=****]";
    }
}

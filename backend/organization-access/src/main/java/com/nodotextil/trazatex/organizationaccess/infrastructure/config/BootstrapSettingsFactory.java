package com.nodotextil.trazatex.organizationaccess.infrastructure.config;

import com.nodotextil.trazatex.organizationaccess.application.dto.BootstrapSettings;
import com.nodotextil.trazatex.organizationaccess.domain.model.Email;

import java.util.ArrayList;
import java.util.List;

/** Valida las variables {@code TRAZATEX_*} del bootstrap (ADR-013): todas son obligatorias; si falta alguna el servicio no arranca. */
final class BootstrapSettingsFactory {

    private BootstrapSettingsFactory() {
    }

    static BootstrapSettings create(String ownerEmail, String ownerName, String maxCompanies, String maxUsersPerCompany) {
        List<String> problems = new ArrayList<>();
        if (blank(ownerEmail)) {
            problems.add("falta TRAZATEX_OWNER_EMAIL");
        } else {
            try {
                new Email(ownerEmail);
            } catch (RuntimeException e) {
                problems.add("TRAZATEX_OWNER_EMAIL no es un correo válido");
            }
        }
        if (blank(ownerName)) {
            problems.add("falta TRAZATEX_OWNER_NAME");
        }
        Integer companies = positiveInt("TRAZATEX_LICENSE_MAX_COMPANIES", maxCompanies, problems);
        Integer users = positiveInt("TRAZATEX_LICENSE_MAX_USERS_PER_COMPANY", maxUsersPerCompany, problems);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Bootstrap del Dueño de Licencia (ADR-013): " + String.join("; ", problems)
                    + ". No hay valores por defecto.");
        }
        return new BootstrapSettings(new Email(ownerEmail).value(), ownerName.trim(), companies, users);
    }

    private static Integer positiveInt(String variable, String value, List<String> problems) {
        if (blank(value)) {
            problems.add("falta " + variable);
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed >= 1) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // se informa abajo
        }
        problems.add(variable + " debe ser un entero mayor o igual a 1");
        return null;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

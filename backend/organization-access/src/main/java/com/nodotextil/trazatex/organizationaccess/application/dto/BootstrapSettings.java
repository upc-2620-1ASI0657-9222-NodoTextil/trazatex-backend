package com.nodotextil.trazatex.organizationaccess.application.dto;

/** Datos del bootstrap del Dueño de Licencia (ADR-013), ya validados: vienen de las variables {@code TRAZATEX_*}. */
public record BootstrapSettings(String ownerEmail, String ownerName, int maxCompanies, int maxUsersPerCompany) {
}

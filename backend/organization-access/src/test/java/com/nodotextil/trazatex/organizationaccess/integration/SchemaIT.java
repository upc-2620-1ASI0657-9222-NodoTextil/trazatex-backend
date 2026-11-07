package com.nodotextil.trazatex.organizationaccess.integration;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Migraciones Flyway y restricciones de la base (C): lo que la aplicación no puede olvidar porque la base lo impide. */
class SchemaIT extends IntegrationTestBase {

    @Test
    void rolesAreSeededByMigration() {
        assertThat(jdbc.sql("SELECT role_name FROM roles ORDER BY id").query(String.class).list())
                .containsExactly("LICENSE_OWNER", "COMPANY_ADMIN", "OPERATOR");
    }

    @Test
    void userEmailIsUnique() {
        UUID company = insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
        insertUser(company, "Ana", "ana@textil.pe", "COMPANY_ADMIN", "ACTIVE");

        assertThatThrownBy(() -> insertUser(company, "Otra", "ana@textil.pe", "OPERATOR", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void userEmailMustBeLowercase() {
        UUID company = insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
        assertThatThrownBy(() -> insertUser(company, "Ana", "Ana@Textil.pe", "COMPANY_ADMIN", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyTheOwnerHasNoCompany() {
        UUID company = insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
        assertThatThrownBy(() -> insertUser(null, "Ana", "ana@textil.pe", "OPERATOR", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUser(company, "Dueño", "dueno2@textil.pe", "LICENSE_OWNER", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aSecondPendingInvitationForTheSameEmailIsRejected() {
        UUID company = insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
        UUID admin = insertUser(company, "Ana", "ana@textil.pe", "COMPANY_ADMIN", "ACTIVE");
        insertInvitation(company, "nuevo@textil.pe", "OPERATOR", "PENDING", admin, START);

        assertThatThrownBy(() -> insertInvitation(company, "nuevo@textil.pe", "OPERATOR", "PENDING", admin, START))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theSameEmailMayHaveManyNonPendingInvitationsAndOnePending() {
        UUID company = insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
        UUID admin = insertUser(company, "Ana", "ana@textil.pe", "COMPANY_ADMIN", "ACTIVE");
        insertInvitation(company, "nuevo@textil.pe", "OPERATOR", "CANCELLED", admin, START);
        insertInvitation(company, "nuevo@textil.pe", "OPERATOR", "EXPIRED", admin, START);
        insertInvitation(company, "nuevo@textil.pe", "OPERATOR", "PENDING", admin, START);

        assertThat(count("SELECT count(*) FROM invitations WHERE email = 'nuevo@textil.pe'")).isEqualTo(3);
    }

    @Test
    void thereCanBeOnlyOneLicense() {
        assertThatThrownBy(() -> insertLicense(5, 5)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rucIsUniqueAndHasElevenDigits() {
        insertCompany(VALID_RUC, "Hilos SAC", "ACTIVE");
        assertThatThrownBy(() -> insertCompany(VALID_RUC, "Otra SAC", "ACTIVE")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCompany("123", "Corta SAC", "ACTIVE")).isInstanceOf(DataIntegrityViolationException.class);
    }
}

package com.nodotextil.trazatex.organizationaccess.infrastructure.config;

import com.nodotextil.trazatex.organizationaccess.application.dto.BootstrapSettings;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BootstrapSettingsFactoryTest {

    @Test
    void buildsTheSettingsNormalizingTheEmail() {
        BootstrapSettings settings = BootstrapSettingsFactory.create(" Dueña@NodoTextil.pe ", " Rosa Díaz ", "5", " 10 ");

        assertThat(settings).isEqualTo(new BootstrapSettings("dueña@nodotextil.pe", "Rosa Díaz", 5, 10));
    }

    @Test
    void everyMissingVariableIsListedAndNothingHasADefault() {
        assertThatThrownBy(() -> BootstrapSettingsFactory.create("", null, " ", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TRAZATEX_OWNER_EMAIL")
                .hasMessageContaining("TRAZATEX_OWNER_NAME")
                .hasMessageContaining("TRAZATEX_LICENSE_MAX_COMPANIES")
                .hasMessageContaining("TRAZATEX_LICENSE_MAX_USERS_PER_COMPANY");
    }

    @Test
    void rejectsAnInvalidEmailAndNonPositiveOrNonNumericLimits() {
        assertThatThrownBy(() -> BootstrapSettingsFactory.create("no-es-correo", "Rosa", "0", "diez"))
                .hasMessageContaining("TRAZATEX_OWNER_EMAIL no es un correo válido")
                .hasMessageContaining("TRAZATEX_LICENSE_MAX_COMPANIES debe ser un entero")
                .hasMessageContaining("TRAZATEX_LICENSE_MAX_USERS_PER_COMPANY debe ser un entero");
    }
}

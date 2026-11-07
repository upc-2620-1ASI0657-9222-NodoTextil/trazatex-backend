package com.nodotextil.trazatex.quality.acceptance;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

/** Los colaboradores de los pasos. Solo existen en el contexto de los escenarios (no se detectan por el escaneo de la aplicación). */
@TestConfiguration
class AcceptanceBeans {

    /** El interruptor del generador de PDF (US44 esc. 2): envuelve al generador real. */
    @Bean
    static org.springframework.beans.factory.config.BeanPostProcessor pdfSwitchInstaller() {
        return com.nodotextil.trazatex.quality.integration.PdfSwitch.installer();
    }

    @Bean
    QualityApi qualityApi(Environment environment, ObjectMapper json) {
        return new QualityApi(environment, json);
    }

    /** Un {@code World} nuevo por escenario, SIN proxy (los campos públicos de un proxy no son los del objeto real). */
    @Bean
    @Scope("cucumber-glue")
    World world() {
        return new World();
    }
}

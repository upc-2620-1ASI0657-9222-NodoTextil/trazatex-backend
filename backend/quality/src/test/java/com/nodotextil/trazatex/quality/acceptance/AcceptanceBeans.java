package com.nodotextil.trazatex.quality.acceptance;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

@TestConfiguration
class AcceptanceBeans {

    @Bean
    static org.springframework.beans.factory.config.BeanPostProcessor pdfSwitchInstaller() {
        return com.nodotextil.trazatex.quality.integration.PdfSwitch.installer();
    }

    @Bean
    QualityApi qualityApi(Environment environment, ObjectMapper json) {
        return new QualityApi(environment, json);
    }

    @Bean
    @Scope("cucumber-glue")
    World world() {
        return new World();
    }
}

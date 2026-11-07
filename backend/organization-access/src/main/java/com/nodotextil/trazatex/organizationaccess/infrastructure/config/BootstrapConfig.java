package com.nodotextil.trazatex.organizationaccess.infrastructure.config;

import com.nodotextil.trazatex.organizationaccess.application.dto.BootstrapSettings;
import com.nodotextil.trazatex.organizationaccess.application.port.in.BootstrapOwnerUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;

@Configuration
class BootstrapConfig {

    @Bean
    BootstrapSettings bootstrapSettings(@Value("${trazatex.bootstrap.owner-email:}") String ownerEmail,
                                        @Value("${trazatex.bootstrap.owner-name:}") String ownerName,
                                        @Value("${trazatex.bootstrap.max-companies:}") String maxCompanies,
                                        @Value("${trazatex.bootstrap.max-users-per-company:}") String maxUsersPerCompany) {
        return BootstrapSettingsFactory.create(ownerEmail, ownerName, maxCompanies, maxUsersPerCompany);
    }

    /** Corre al terminar de arrancar el contexto (las migraciones Flyway ya se aplicaron). */
    @Bean
    ApplicationRunner ownerBootstrapRunner(BootstrapOwnerUseCase bootstrap) {
        Logger log = LoggerFactory.getLogger(BootstrapConfig.class);
        return (ApplicationArguments args) -> {
            BootstrapOwnerUseCase.Outcome outcome;
            try {
                outcome = bootstrap.run();
            } catch (DataIntegrityViolationException concurrentInstance) {
                // otra instancia creó la licencia en el mismo instante: se vuelve a evaluar con la licencia ya creada
                outcome = bootstrap.run();
            }
            log.info("Bootstrap del Dueño de Licencia: {}", outcome);
        };
    }
}

package com.nodotextil.trazatex.quality.acceptance;

import com.nodotextil.trazatex.quality.integration.CloudinaryStub;
import com.nodotextil.trazatex.quality.integration.ProductionStub;
import com.nodotextil.trazatex.quality.integration.SharedPostgres;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "trazatex.messaging.enabled=false", "trazatex.quality.start.job-enabled=false", "trazatex.idempotency.cleanup-job-delay=PT24H",
        "trazatex.production.read-timeout=PT2S", "trazatex.evidence.upload.enabled=false",
        "trazatex.cloudinary.url=cloudinary://123456789012345:secreto-de-prueba@demo"})
@Import(AcceptanceBeans.class)
public class AcceptanceSpringConfiguration {

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedPostgres.CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", SharedPostgres.CONTAINER::getUsername);
        registry.add("spring.datasource.password", SharedPostgres.CONTAINER::getPassword);
        registry.add("trazatex.production.base-url", ProductionStub::baseUrl);
        registry.add("trazatex.cloudinary.base-url", CloudinaryStub.SERVER::baseUrl);
    }
}

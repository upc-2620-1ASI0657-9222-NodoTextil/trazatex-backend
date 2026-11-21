package com.nodotextil.trazatex.production.acceptance;

import com.nodotextil.trazatex.production.integration.SharedPostgres;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "trazatex.messaging.enabled=false", "trazatex.evidence.upload.enabled=false", "trazatex.idempotency.cleanup-job-delay=PT24H",
        "trazatex.cloudinary.read-timeout=PT2S"})
@Import(AcceptanceBeans.class)
public class AcceptanceSpringConfiguration {

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedPostgres.CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", SharedPostgres.CONTAINER::getUsername);
        registry.add("spring.datasource.password", SharedPostgres.CONTAINER::getPassword);
        registry.add("trazatex.cloudinary.url", () -> "cloudinary://123456789012345:secreto-de-aceptacion@demo");
        registry.add("trazatex.cloudinary.base-url", CloudinaryStub.SERVER::baseUrl);
    }
}

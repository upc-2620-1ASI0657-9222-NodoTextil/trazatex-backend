package com.nodotextil.trazatex.quality.acceptance;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Escenarios de aceptación (RNF-10) de US23 a US28, US34, US43 y US44, en español y con el texto exacto de {@code docs/domain/user-stories.md}.
 * Corren dentro de {@code ./mvnw verify} (Failsafe). Servicio real por HTTP + PostgreSQL de Testcontainers + Cloudinary simulado.
 * Requiere Docker.
 */
@Suite
@IncludeEngines("cucumber")
@SelectPackages("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.nodotextil.trazatex.quality.acceptance")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "summary")
public class CucumberIT {
}

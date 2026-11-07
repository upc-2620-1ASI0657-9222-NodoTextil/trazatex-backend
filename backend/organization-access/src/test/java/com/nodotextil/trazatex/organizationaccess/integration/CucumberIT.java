package com.nodotextil.trazatex.organizationaccess.integration;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Escenarios de aceptación (RNF-10) de US01–US09, US11 y US41, en español y con el texto exacto de
 * {@code docs/domain/user-stories.md} (US10, maquinaria, es de Production). Spring completo + PostgreSQL real; Chequea.pe
 * y HIBP simulados con WireMock. Requiere Docker.
 */
@Suite
@IncludeEngines("cucumber")
@SelectPackages("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.nodotextil.trazatex.organizationaccess.integration")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "summary")
public class CucumberIT {
}

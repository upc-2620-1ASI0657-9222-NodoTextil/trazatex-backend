package com.nodotextil.trazatex.organizationaccess.integration;

import io.cucumber.spring.CucumberContextConfiguration;

/** El contexto de Spring de los escenarios es el mismo de las pruebas de integración (anotaciones heredadas de la base). */
@CucumberContextConfiguration
public class CucumberSpringConfiguration extends IntegrationTestBase {
}

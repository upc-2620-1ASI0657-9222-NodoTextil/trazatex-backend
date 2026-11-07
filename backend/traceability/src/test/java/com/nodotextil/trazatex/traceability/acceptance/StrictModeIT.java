package com.nodotextil.trazatex.traceability.acceptance;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.util.ArrayList;
import java.util.List;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.OBJECT_FACTORY_PROPERTY_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathResource;
import static org.junit.platform.launcher.EngineFilter.includeEngines;

/**
 * Modo estricto: un paso sin definir, pendiente o ambiguo hace FALLAR el escenario (no lo omite ni lo da por bueno). Se comprueba
 * lanzando el motor de Cucumber con .feature de ejemplo ({@code strictness/}) y exigiendo que el resultado sea {@code FAILED}; el
 * escenario de control, con un paso definido, debe pasar (así se sabe que el lanzador funciona). Un paso con el cuerpo vacío no se
 * puede detectar al ejecutar: lo vigila {@code StepDefinitionsTest}.
 */
class StrictModeIT {

    private record Outcome(String name, TestExecutionResult.Status status, Throwable error) {
    }

    private List<Outcome> run(String feature) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClasspathResource("strictness/" + feature))
                .filters(includeEngines("cucumber"))
                .configurationParameter(GLUE_PROPERTY_NAME, "com.nodotextil.trazatex.traceability.strictness")
                .configurationParameter(OBJECT_FACTORY_PROPERTY_NAME, "io.cucumber.core.backend.DefaultObjectFactory")
                .build();
        List<Outcome> outcomes = new ArrayList<>();
        Launcher launcher = LauncherFactory.create();
        launcher.execute(request, new TestExecutionListener() {
            @Override
            public void executionFinished(TestIdentifier identifier, TestExecutionResult result) {
                if (identifier.isTest()) {
                    outcomes.add(new Outcome(identifier.getDisplayName(), result.getStatus(), result.getThrowable().orElse(null)));
                }
            }
        });
        return outcomes;
    }

    @Test
    void aScenarioWithDefinedStepsPasses() {
        assertThat(run("control.feature")).singleElement().satisfies(outcome -> assertThat(outcome.status()).isEqualTo(TestExecutionResult.Status.SUCCESSFUL));
    }

    @Test
    void anUndefinedStepFailsTheScenario() {
        assertThat(run("undefined.feature")).singleElement().satisfies(outcome -> assertThat(outcome.status())
                .as("paso sin definir: %s", outcome.error()).isEqualTo(TestExecutionResult.Status.FAILED));
    }

    @Test
    void aPendingStepFailsTheScenario() {
        assertThat(run("pending.feature")).singleElement().satisfies(outcome -> assertThat(outcome.status())
                .as("paso pendiente: %s", outcome.error()).isEqualTo(TestExecutionResult.Status.FAILED));
    }

    @Test
    void anAmbiguousStepFailsTheScenario() {
        assertThat(run("ambiguous.feature")).singleElement().satisfies(outcome -> assertThat(outcome.status())
                .as("paso ambiguo: %s", outcome.error()).isEqualTo(TestExecutionResult.Status.FAILED));
    }
}

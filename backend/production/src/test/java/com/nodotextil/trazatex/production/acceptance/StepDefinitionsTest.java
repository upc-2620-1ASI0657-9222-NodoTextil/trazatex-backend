package com.nodotextil.trazatex.production.acceptance;

import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglas sobre los pasos de los escenarios, para que un paso no sea de adorno (el modo estricto atrapa lo que falta o está pendiente; esto,
 * lo que existe pero no hace nada):
 * <ul>
 *   <li>Ningún paso tiene el cuerpo vacío: todo paso hace al menos una llamada.</li>
 *   <li>Todo {@code Entonces} (y los {@code Y} que lo continúan) llega a una aserción de AssertJ, directamente o a través de los
 *       métodos auxiliares de las clases de pasos.</li>
 *   <li>Los pasos hablan con el servicio solo por HTTP: no usan MockMvc, Mockito, los casos de uso, el dominio ni la infraestructura.</li>
 * </ul>
 */
class StepDefinitionsTest {

    private static final String ACCEPTANCE = "com.nodotextil.trazatex.production.acceptance";
    private static final List<Class<? extends Annotation>> STEP_ANNOTATIONS = List.of(io.cucumber.java.es.Dado.class,
            io.cucumber.java.es.Cuando.class, io.cucumber.java.es.Entonces.class, io.cucumber.java.es.Y.class);
    private static final List<Class<? extends Annotation>> THEN_ANNOTATIONS = List.of(io.cucumber.java.es.Entonces.class, io.cucumber.java.es.Y.class);

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS).importPackages(ACCEPTANCE);
    }

    private static boolean isStep(JavaMethod method, List<Class<? extends Annotation>> annotations) {
        return annotations.stream().anyMatch(method::isAnnotatedWith);
    }

    private static boolean reachesAnAssertion(JavaMethod method, Set<String> visited) {
        if (!visited.add(method.getFullName())) {
            return false;
        }
        for (JavaCall<?> call : method.getCallsFromSelf()) {
            if (call.getTargetOwner().getPackageName().startsWith("org.assertj")) {
                return true;
            }
            JavaMember target = call.getTarget().resolveMember().orElse(null);
            if (target instanceof JavaMethod next && next.getOwner().getPackageName().startsWith(ACCEPTANCE) && reachesAnAssertion(next, visited)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void thereAreStepsToCheck() {
        List<JavaMethod> steps = classes.stream().flatMap(c -> c.getMethods().stream()).filter(m -> isStep(m, STEP_ANNOTATIONS)).toList();
        assertThat(steps.size()).as("pasos definidos").isGreaterThan(80);
        assertThat(steps.stream().filter(m -> isStep(m, THEN_ANNOTATIONS)).count()).as("pasos Entonces/Y").isGreaterThan(40);
    }

    @Test
    void noStepHasAnEmptyBody() {
        methods().that(new com.tngtech.archunit.base.DescribedPredicate<JavaMethod>("son pasos") {
            @Override
            public boolean test(JavaMethod method) {
                return isStep(method, STEP_ANNOTATIONS);
            }
        }).should(new ArchCondition<JavaMethod>("hacer al menos una llamada") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (method.getCallsFromSelf().isEmpty()) {
                    events.add(SimpleConditionEvent.violated(method, method.getFullName() + " es un paso vacío"));
                }
            }
        }).check(classes);
    }

    @Test
    void everyThenStepReachesAnAssertion() {
        methods().that(new com.tngtech.archunit.base.DescribedPredicate<JavaMethod>("son pasos Entonces o Y") {
            @Override
            public boolean test(JavaMethod method) {
                return isStep(method, THEN_ANNOTATIONS);
            }
        }).should(new ArchCondition<JavaMethod>("llegar a una aserción de AssertJ") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (!reachesAnAssertion(method, new HashSet<>())) {
                    events.add(SimpleConditionEvent.violated(method, method.getFullName() + " no comprueba nada"));
                }
            }
        }).check(classes);
    }

    @Test
    void stepsOnlyTalkToTheServiceOverHttp() {
        noClasses().that().resideInAPackage(ACCEPTANCE + "..")
                .and().doNotHaveSimpleName("StepDefinitionsTest").and().doNotHaveSimpleName("UserStoriesComparator")
                .and().doNotHaveSimpleName("FeaturesMatchUserStoriesTest").and().doNotHaveSimpleName("StrictModeIT")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.test.web.servlet..", "org.mockito..",
                        "com.nodotextil.trazatex.production.application..", "com.nodotextil.trazatex.production.infrastructure..", "com.nodotextil.trazatex.production.domain.model..", "com.nodotextil.trazatex.production.domain.service..",
                        "com.nodotextil.trazatex.production.domain.event..", "com.nodotextil.trazatex.production.domain.exception..")
                .because("los escenarios ejercitan el servicio real por HTTP: ni dobles ni casos de uso ni el dominio")
                .check(classes);
    }
}

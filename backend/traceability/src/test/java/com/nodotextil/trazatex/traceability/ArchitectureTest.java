package com.nodotextil.trazatex.traceability;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Reglas de dependencia hexagonal (docs/conventions.md). Las de `domain` y `application` se activan al existir esos paquetes. */
@AnalyzeClasses(packages = "com.nodotextil.trazatex.traceability", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "com.nodotextil.trazatex.traceability";

    @ArchTest
    static final ArchRule domainOnlyDependsOnJavaItselfAndTheSharedKernelExceptions = classes()
            .that().resideInAPackage(ROOT + ".domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "java..", ROOT + ".domain..", "com.nodotextil.trazatex.sharedkernel.exception..")
            .because("el dominio no importa Spring, Neo4j, Kafka, Jackson ni capas externas")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainDoesNotDependOnApplicationOrInfrastructure = noClasses()
            .that().resideInAPackage(ROOT + ".domain..")
            .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".application..", ROOT + ".infrastructure..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationDoesNotDependOnInfrastructure = noClasses()
            .that().resideInAPackage(ROOT + ".application..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".infrastructure..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule controllersOnlyCallUseCases = noClasses()
            .that().resideInAPackage(ROOT + ".infrastructure.web..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    ROOT + ".infrastructure.persistence..", ROOT + ".application.port.out..", ROOT + ".application.usecase..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationDoesNotKnowNeo4jNorKafka = noClasses()
            .that().resideInAnyPackage(ROOT + ".application..", ROOT + ".domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.neo4j..", "org.springframework.data.neo4j..",
                    "org.apache.kafka..", "org.springframework.kafka..", "com.nodotextil.trazatex.sharedmessaging..")
            .because("Neo4j y Kafka son adaptadores: el dominio y los casos de uso hablan en puertos")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule kafkaListenersOnlyCallInboundPorts = noClasses()
            .that().resideInAPackage(ROOT + ".infrastructure.messaging..")
            .and().haveSimpleNameEndingWith("Listener")
            .should().dependOnClassesThat().resideInAnyPackage(
                    ROOT + ".application.usecase..", ROOT + ".application.port.out..", ROOT + ".infrastructure.persistence..")
            .because("los listeners traducen mensajes a llamadas de los puertos de entrada (application.port.in)");

    // ---- ninguna respuesta de consulta sale sin pasar por la política de privacidad (ADR-006) ----

    private static final String VIEW = ROOT + ".domain.model.view..";
    private static final String RAW = ROOT + ".domain.model.graph..";

    @ArchTest
    static final ArchRule onlyThePolicyBuildsWhatAQueryCanReturn = noClasses()
            .that().doNotHaveFullyQualifiedName(ROOT + ".domain.service.LineageAuthorizationPolicy")
            .should().callConstructorWhere(target(owner(assignableTo(ROOT + ".domain.model.view.VisibleView"))))
            .because("los tipos de domain.model.view son lo único que una consulta puede devolver y solo LineageAuthorizationPolicy los construye: "
                    + "ninguna respuesta sale sin pasar por la política de privacidad");

    @ArchTest
    static final ArchRule theQueryUseCaseOnlyReturnsWhatThePolicyBuilt = methods()
            .that().areDeclaredIn(ROOT + ".application.port.in.QueryLineageUseCase")
            .should().haveRawReturnType(resideInAPackage(VIEW))
            .because("las consultas devuelven solo tipos ya filtrados por la política");

    @ArchTest
    static final ArchRule theWebLayerNeverSeesUnfilteredGraphData = noClasses()
            .that().resideInAPackage(ROOT + ".infrastructure.web..")
            .should().dependOnClassesThat().resideInAnyPackage(RAW, ROOT + ".infrastructure.persistence..", ROOT + ".application.port.out..")
            .because("la capa web no puede tocar datos del grafo sin filtrar: solo recibe lo que ya pasó por la política")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule everyHandlerReturnsOnlyFilteredTypes = methods()
            .that().areDeclaredInClassesThat().resideInAPackage(ROOT + ".infrastructure.web..")
            .and().areAnnotatedWith(org.springframework.web.bind.annotation.GetMapping.class)
            .should().haveRawReturnType(resideInAPackage(VIEW))
            .because("un endpoint de consulta devuelve solo tipos de domain.model.view")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule everyEndpointDeclaresWhoMayCallIt = methods()
            .that().areDeclaredInClassesThat().resideInAPackage(ROOT + ".infrastructure.web..")
            .and().areAnnotatedWith(org.springframework.web.bind.annotation.GetMapping.class)
            .should().beAnnotatedWith(ROOT + ".infrastructure.web.security.RequiresRole")
            .because("un endpoint sin @RequiresRole dejaría pasar al Dueño de Licencia (sin empresa): todo handler declara sus roles")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule thereAreNoWritingEndpoints = methods()
            .that().areDeclaredInClassesThat().resideInAPackage(ROOT + ".infrastructure.web..")
            .should().notBeAnnotatedWith(org.springframework.web.bind.annotation.PostMapping.class)
            .andShould().notBeAnnotatedWith(org.springframework.web.bind.annotation.PutMapping.class)
            .andShould().notBeAnnotatedWith(org.springframework.web.bind.annotation.PatchMapping.class)
            .andShould().notBeAnnotatedWith(org.springframework.web.bind.annotation.DeleteMapping.class)
            .because("Traceability solo expone consultas (los cambios llegan por eventos): si algún día hubiera una escritura necesitaría Idempotency-Key (ADR-010)")
            .allowEmptyShould(true);

    // ---- el análisis de impacto es interno: ve todas las empresas y su resultado solo sale por el evento ----

    private static final String IMPACT_MODEL = ROOT + ".domain.model.impact..";

    @ArchTest
    static final ArchRule theImpactAnalysisNeverGoesThroughThePrivacyPolicyNorBuildsViews = noClasses()
            .that().haveSimpleNameStartingWith("Impact").or().resideInAPackage(IMPACT_MODEL)
            .should().dependOnClassesThat().haveFullyQualifiedName(ROOT + ".domain.service.LineageAuthorizationPolicy")
            .orShould().dependOnClassesThat().resideInAPackage(VIEW)
            .because("el análisis de impacto es interno y debe ver los lotes de todas las empresas: no pasa por el filtro B2B ni arma respuestas de consulta")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule theWebLayerNeverTouchesTheImpactAnalysis = noClasses()
            .that().resideInAPackage(ROOT + ".infrastructure.web..")
            .should().dependOnClassesThat().resideInAPackage(IMPACT_MODEL)
            .orShould().dependOnClassesThat().haveSimpleNameStartingWith("Impact")
            .because("el resultado del análisis de impacto solo sale por el evento PossibleDerivedFailureDetected: ninguna ruta HTTP lo devuelve")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule theQueriesNeverUseTheImpactAnalysis = noClasses()
            .that().haveSimpleNameStartingWith("Lineage")
            .should().dependOnClassesThat().haveSimpleNameStartingWith("Impact")
            .because("las consultas y el análisis de impacto son dos casos de uso distintos: uno filtra por empresa y el otro no")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule unfilteredGraphDataStaysInsideTheReadSide = noClasses()
            .that().resideOutsideOfPackages(RAW, ROOT + ".domain.service..", ROOT + ".application..", ROOT + ".infrastructure.persistence..")
            .should().dependOnClassesThat().resideInAPackage(RAW)
            .because("los datos del grafo sin filtrar solo los tocan la política, la aplicación y el adaptador de persistencia")
            .allowEmptyShould(true);

    /** I11: ni un controlador ni un caso de uso tocan la base; el SQL vive en los adaptadores de {@code infrastructure.persistence}. */
    @ArchTest
    static final ArchRule neitherControllersNorUseCasesUseJdbc = noClasses()
            .that().resideInAnyPackage(ROOT + ".infrastructure.web..", ROOT + ".application..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..")
            .because("regla 5 de CLAUDE.md: el acceso a datos es un adaptador detrás de un puerto de salida")
            .allowEmptyShould(true);
}

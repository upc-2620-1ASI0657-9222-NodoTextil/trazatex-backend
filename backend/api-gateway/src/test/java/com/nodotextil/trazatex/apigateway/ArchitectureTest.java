package com.nodotextil.trazatex.apigateway;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Reglas de dependencia hexagonal (docs/conventions.md). El Gateway es reactivo: {@code application} puede usar Reactor. */
@AnalyzeClasses(packages = "com.nodotextil.trazatex.apigateway", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "com.nodotextil.trazatex.apigateway";

    @ArchTest
    static final ArchRule domainOnlyDependsOnJava = classes()
            .that().resideInAPackage(ROOT + ".domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", ROOT + ".domain..")
            .because("el dominio no importa Spring, Kafka, Nimbus ni capas externas");

    @ArchTest
    static final ArchRule applicationOnlyDependsOnDomainReactorAndItself = classes()
            .that().resideInAPackage(ROOT + ".application..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "reactor..", ROOT + ".domain..", ROOT + ".application..")
            .because("los casos de uso hablan con el exterior solo por puertos");

    @ArchTest
    static final ArchRule noOneDependsOnInfrastructureFromInside = noClasses()
            .that().resideInAnyPackage(ROOT + ".domain..", ROOT + ".application..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".infrastructure..");

    @ArchTest
    static final ArchRule gatewayDoesNotDependOnSharedKernel = noClasses()
            .that().resideInAPackage(ROOT + "..")
            .should().dependOnClassesThat().resideInAPackage("com.nodotextil.trazatex.sharedkernel..")
            .because("el Gateway es reactivo y no depende de shared-kernel (progress.md)");

    /** I11: ni un controlador ni un caso de uso tocan la base; el SQL vive en los adaptadores de {@code infrastructure.persistence}. */
    @ArchTest
    static final ArchRule neitherControllersNorUseCasesUseJdbc = noClasses()
            .that().resideInAnyPackage(ROOT + ".infrastructure.web..", ROOT + ".application..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..")
            .because("regla 5 de CLAUDE.md: el acceso a datos es un adaptador detrás de un puerto de salida")
            .allowEmptyShould(true);
}

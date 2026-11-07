package com.nodotextil.trazatex.organizationaccess;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Reglas de dependencia hexagonal (docs/conventions.md). */
@AnalyzeClasses(packages = "com.nodotextil.trazatex.organizationaccess", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "com.nodotextil.trazatex.organizationaccess";

    @ArchTest
    static final ArchRule domainOnlyDependsOnJavaItselfAndTheSharedKernelExceptions = classes()
            .that().resideInAPackage(ROOT + ".domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "java..", ROOT + ".domain..", "com.nodotextil.trazatex.sharedkernel.exception..")
            .because("el dominio no importa Spring, JPA, Kafka, Jackson ni capas externas");

    @ArchTest
    static final ArchRule domainDoesNotDependOnApplicationOrInfrastructure = noClasses()
            .that().resideInAPackage(ROOT + ".domain..")
            .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".application..", ROOT + ".infrastructure..");

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

    /** I11: ni un controlador ni un caso de uso tocan la base; el SQL vive en los adaptadores de {@code infrastructure.persistence}. */
    @ArchTest
    static final ArchRule neitherControllersNorUseCasesUseJdbc = noClasses()
            .that().resideInAnyPackage(ROOT + ".infrastructure.web..", ROOT + ".application..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..")
            .because("regla 5 de CLAUDE.md: el acceso a datos es un adaptador detrás de un puerto de salida")
            .allowEmptyShould(true);
}

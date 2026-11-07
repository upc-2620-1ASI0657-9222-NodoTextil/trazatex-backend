package com.nodotextil.trazatex.production;

import com.nodotextil.trazatex.production.application.port.out.BatchRepositoryPort;
import com.nodotextil.trazatex.production.domain.model.Batch;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.util.Collection;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Reglas de dependencia hexagonal (docs/conventions.md). */
@AnalyzeClasses(packages = "com.nodotextil.trazatex.production", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "com.nodotextil.trazatex.production";

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

    @ArchTest
    static final ArchRule modelDoesNotDependOnDomainServices = noClasses()
            .that().resideInAPackage(ROOT + ".domain.model..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".domain.service..")
            .because("el modelo define la interfaz TransformationStrategy; las implementaciones y la fábrica dependen de él, no al revés");

    @ArchTest
    static final ArchRule onlyBatchChangesSavesBatches = noClasses()
            .that().doNotHaveFullyQualifiedName(ROOT + ".application.usecase.BatchChanges")
            .and().doNotHaveFullyQualifiedName(BatchRepositoryPort.class.getName())
            .should().callMethod(BatchRepositoryPort.class, "save", Batch.class)
            .orShould().callMethod(BatchRepositoryPort.class, "saveAll", Collection.class)
            .because("BatchChanges es el único camino para guardar lotes: publica BatchPhaseChanged por cada cambio de fase o "
                    + "retención (docs/architecture/communication.md); guardar por otro lado lo saltaría");

    @ArchTest
    static final ArchRule applicationDoesNotKnowKafkaNorTheMessagingModule = noClasses()
            .that().resideInAnyPackage(ROOT + ".application..", ROOT + ".domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.apache.kafka..", "org.springframework.kafka..",
                    "com.nodotextil.trazatex.sharedmessaging..")
            .because("Kafka es un adaptador: el dominio y los casos de uso reciben órdenes, no mensajes");

    @ArchTest
    static final ArchRule kafkaListenersOnlyCallInboundPorts = noClasses()
            .that().resideInAPackage(ROOT + ".infrastructure.messaging..")
            .and().haveSimpleNameEndingWith("Listener")
            .should().dependOnClassesThat().resideInAnyPackage(
                    ROOT + ".application.usecase..", ROOT + ".application.port.out..", ROOT + ".infrastructure.persistence..")
            .because("los listeners traducen mensajes a llamadas de los puertos de entrada (application.port.in)");

    /** I11: ni un controlador ni un caso de uso tocan la base; el SQL vive en los adaptadores de {@code infrastructure.persistence}. */
    @ArchTest
    static final ArchRule neitherControllersNorUseCasesUseJdbc = noClasses()
            .that().resideInAnyPackage(ROOT + ".infrastructure.web..", ROOT + ".application..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..")
            .because("regla 5 de CLAUDE.md: el acceso a datos es un adaptador detrás de un puerto de salida")
            .allowEmptyShould(true);
}

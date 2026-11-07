package com.nodotextil.trazatex.quality;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/** Reglas de dependencia hexagonal (docs/conventions.md). */
@AnalyzeClasses(packages = "com.nodotextil.trazatex.quality", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "com.nodotextil.trazatex.quality";

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
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".infrastructure..");

    @ArchTest
    static final ArchRule controllersOnlyCallUseCases = noClasses()
            .that().resideInAPackage(ROOT + ".infrastructure.web..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    ROOT + ".infrastructure.persistence..", ROOT + ".application.port.out..", ROOT + ".application.usecase..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainEventsDependOnTheModelButTheModelDoesNotKnowThem = noClasses()
            .that().resideInAPackage(ROOT + ".domain.model..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".domain.event..");

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

    @ArchTest
    static final ArchRule theOnlyRestCallToAnotherServiceIsTheProductionPhaseClient = noClasses()
            .that().resideOutsideOfPackage(ROOT + ".infrastructure.external..")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.web.client.RestClient")
            .because("entre microservicios no hay llamadas REST salvo el comando Quality → Production (ADR-009), detrás de ProductionPhasePort");

    @ArchTest
    static final ArchRule productionIsOnlyReachedThroughItsPort = noClasses()
            .that().resideOutsideOfPackage(ROOT + ".infrastructure..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".infrastructure.external..");

    /**
     * Estas clases llaman a Production: no pueden correr dentro de una transacción del llamador (el {@code IdempotencyFilter} abre una
     * por petición). Cada guardado usa su propia transacción ({@code REQUIRES_NEW}); un {@code @Transactional} aquí devolvería el
     * control {@code STARTING} y el {@code CANCELLED} a la transacción de la petición, que un rechazo o una caída revierte.
     */
    @ArchTest
    static final ArchRule classesThatCallProductionAreNotTransactional = noClasses()
            .that().haveSimpleNameStartingWith("ControlStart").or().haveSimpleNameStartingWith("ControlFinish").or().haveSimpleNameStartingWith("FailureDiscard")
            .or().haveSimpleName("DerivedHoldResolver").or().haveSimpleName("PendingCommandRecoveryService").or().haveSimpleName("PendingRetryScheduler")
            .or().haveSimpleNameStartingWith("PendingCommandSources")
            .should().beAnnotatedWith(Transactional.class);

    @ArchTest
    static final ArchRule norAreTheirMethods = noMethods()
            .that().areDeclaredInClassesThat().haveSimpleNameStartingWith("ControlStart").or().areDeclaredInClassesThat().haveSimpleNameStartingWith("ControlFinish")
            .or().areDeclaredInClassesThat().haveSimpleNameStartingWith("FailureDiscard").or().areDeclaredInClassesThat().haveSimpleName("DerivedHoldResolver")
            .or().areDeclaredInClassesThat().haveSimpleName("PendingCommandRecoveryService")
            .or().areDeclaredInClassesThat().haveSimpleNameStartingWith("PendingCommandSources")
            .should().beAnnotatedWith(Transactional.class);

    /**
     * US44: el reporte se genera en memoria y no se guarda («no lo almacena dentro del sistema»): ni el servicio ni el generador ni el endpoint
     * pueden usar archivos del disco, ni temporales.
     */
    @ArchTest
    static final ArchRule theReportIsNeverWrittenToDisk = noClasses()
            .that().haveSimpleName("ControlReportService").or().haveSimpleName("OpenHtmlToPdfAdapter").or().haveSimpleName("ReportConfig")
            .or().haveSimpleName("QualityControlController")
            .should().dependOnClassesThat().haveFullyQualifiedName("java.io.File")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.io.FileOutputStream")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.io.FileWriter")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.nio.file.Files")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.nio.file.Path")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.nio.channels.FileChannel");

    /** El servicio del reporte genera el PDF fuera de toda transacción (puede tardar); solo su auditoría abre una, corta. */
    @ArchTest
    static final ArchRule theReportServiceIsNotTransactional = noClasses()
            .that().haveSimpleName("ControlReportService")
            .should().beAnnotatedWith(Transactional.class);

    /** El generador local no habla con la red: ninguna clase del generador usa un cliente HTTP. */
    @ArchTest
    static final ArchRule thePdfGeneratorHasNoNetworkClient = noClasses()
            .that().haveSimpleName("OpenHtmlToPdfAdapter")
            .should().dependOnClassesThat().resideInAnyPackage("java.net.http..", "org.springframework.web.client..", "java.net")
            .because("el generador no puede cargar recursos externos");

    /** I11: ni un controlador ni un caso de uso tocan la base; el SQL vive en los adaptadores de {@code infrastructure.persistence}. */
    @ArchTest
    static final ArchRule neitherControllersNorUseCasesUseJdbc = noClasses()
            .that().resideInAnyPackage(ROOT + ".infrastructure.web..", ROOT + ".application..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..")
            .because("regla 5 de CLAUDE.md: el acceso a datos es un adaptador detrás de un puerto de salida")
            .allowEmptyShould(true);
}

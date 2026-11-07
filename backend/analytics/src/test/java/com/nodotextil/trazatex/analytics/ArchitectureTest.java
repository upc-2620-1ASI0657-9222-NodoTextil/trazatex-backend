package com.nodotextil.trazatex.analytics;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {
    private final com.tngtech.archunit.core.domain.JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.nodotextil.trazatex.analytics");

    @Test void applicationUsesPortsInsteadOfPersistenceMessagingOrWebAdapters() {
        noClasses().that().resideInAnyPackage("..application..","..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..","org.springframework.jdbc..",
                        "org.springframework.kafka..","org.springframework.web..","org.neo4j..","jakarta.persistence..","jakarta.servlet..")
                .check(classes);
    }

    @Test void theDomainDependsOnNothingButTheSharedKernel() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..application..","org.springframework..","tools.jackson..","com.fasterxml..")
                .check(classes);
    }

    @Test void controllersAndListenersNeverTouchTheDatabase() {
        noClasses().that().resideInAnyPackage("..infrastructure.web..","..infrastructure.messaging..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..","java.sql..","..infrastructure.persistence..")
                .check(classes);
    }
}

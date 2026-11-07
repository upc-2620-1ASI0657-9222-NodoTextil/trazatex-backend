package com.nodotextil.trazatex.notification;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {
    private static final com.tngtech.archunit.core.domain.JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.nodotextil.trazatex.notification");

    @Test void applicationUsesPortsInsteadOfPersistenceMessagingOrWebAdapters() {
        noClasses().that().resideInAnyPackage("..application..","..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..","org.springframework.jdbc..",
                        "org.springframework.kafka..","org.springframework.web..","org.neo4j..","jakarta.persistence..")
                .check(CLASSES);
    }

    @Test void controllersAndListenersNeverTouchJdbc() {
        noClasses().that().resideInAnyPackage("..infrastructure.web..","..infrastructure.messaging..","..infrastructure.scheduling..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..","java.sql..")
                .check(CLASSES);
    }

    @Test void onlyTheSendGridAdapterKnowsTheMailProvider() {
        noClasses().that().resideOutsideOfPackage("..infrastructure.external..")
                .should().dependOnClassesThat().haveSimpleNameContaining("SendGrid")
                .check(CLASSES);
    }
}

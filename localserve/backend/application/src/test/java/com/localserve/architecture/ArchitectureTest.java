package com.localserve.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {
    @Test void webLayerMustNotReachPersistenceImplementations() {
        var classes = new ClassFileImporter().importPackages("com.localserve");
        noClasses().that().resideInAnyPackage("..web..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure.mongo..", "..identityinfra..")
                .check(classes);
    }

    @Test void domainLayerMustNotDependOnFrameworks() {
        var classes = new ClassFileImporter().importPackages("com.localserve");
        noClasses().that().resideInAnyPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.servlet..")
                .check(classes);
    }
}

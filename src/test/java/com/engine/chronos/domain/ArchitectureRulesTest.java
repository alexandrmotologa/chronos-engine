package com.engine.chronos.domain;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureRulesTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void setUp() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.engine.chronos");
    }

    @Test
    @DisplayName("Domain layer must not depend on Spring Framework")
    void domainMustNotDependOnSpring() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "jakarta.transaction..",
                        "org.hibernate.."
                )
                .check(importedClasses);
    }

    @Test
    @DisplayName("Domain layer must not depend on Application or Infrastructure layers")
    void domainMustNotDependOnOuterLayers() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..application..",
                        "..infrastructure.."
                )
                .check(importedClasses);
    }

    @Test
    @DisplayName("Domain model classes must be package-private or public and follow DDD rules")
    void domainClassesFollowHexagonalRules() {
        classes()
                .that().resideInAPackage("..domain.model..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        "java..",
                        "..domain.."
                )
                .check(importedClasses);
    }
}

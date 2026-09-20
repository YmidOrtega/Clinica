package com.ClinicaDeYmid.practitioners_service;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "com.ClinicaDeYmid.practitioners_service", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule dependenciesOnlyGoDown = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Web").definedBy("..practitioners_service.web..")
            .layer("Service").definedBy("..practitioners_service.service..")
            .layer("Repository").definedBy("..practitioners_service.repository..")
            .whereLayer("Web").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Web")
            .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service");

    @ArchTest
    static final ArchRule theWebLayerNeverTouchesPersistence = noClasses()
            .that().resideInAPackage("..practitioners_service.web..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..practitioners_service.repository..", "jakarta.persistence..", "org.hibernate..",
                    "org.springframework.data..");

    @ArchTest
    static final ArchRule theSharedCodeDependsOnNoLayer = noClasses()
            .that().resideInAPackage("..practitioners_service.shared..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..practitioners_service.web..", "..practitioners_service.service..",
                    "..practitioners_service.repository..");

    @ArchTest
    static final ArchRule theClientDependsOnNoLayer = noClasses()
            .that().resideInAPackage("..practitioners_service.client..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..practitioners_service.web..", "..practitioners_service.service..",
                    "..practitioners_service.repository..");

    @ArchTest
    static final ArchRule theServiceLayerNeverTouchesTheWeb = noClasses()
            .that().resideInAPackage("..practitioners_service.service..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.web..", "org.springframework.http..", "jakarta.servlet..");

    @ArchTest
    static final ArchRule entitiesRefuseBlindWrites = noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage("..practitioners_service.repository.entity..")
            .and().haveNameMatching("set[A-Z].*")
            .should().bePublic()
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule controllersLiveInTheWebLayer = noClasses()
            .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .should().resideOutsideOfPackage("..practitioners_service.web..");
}

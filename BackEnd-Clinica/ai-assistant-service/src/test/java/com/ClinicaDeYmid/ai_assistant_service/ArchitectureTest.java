package com.ClinicaDeYmid.ai_assistant_service;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "com.ClinicaDeYmid.ai_assistant_service", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule dependenciesOnlyGoDown = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Web").definedBy("..ai_assistant_service.web..")
            .layer("Service").definedBy("..ai_assistant_service.service..")
            .layer("Repository").definedBy("..ai_assistant_service.repository..")
            .whereLayer("Web").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Web")
            .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service");

    @ArchTest
    static final ArchRule theWebLayerNeverTouchesPersistence = noClasses()
            .that().resideInAPackage("..ai_assistant_service.web..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..ai_assistant_service.repository..", "jakarta.persistence..", "org.hibernate..",
                    "org.springframework.data..");

    @ArchTest
    static final ArchRule theSharedCodeDependsOnNoLayer = noClasses()
            .that().resideInAPackage("..ai_assistant_service.shared..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..ai_assistant_service.web..", "..ai_assistant_service.service..",
                    "..ai_assistant_service.repository..");

    @ArchTest
    static final ArchRule theClientDependsOnNoLayer = noClasses()
            .that().resideInAPackage("..ai_assistant_service.client..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..ai_assistant_service.web..", "..ai_assistant_service.service..",
                    "..ai_assistant_service.repository..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule theServiceLayerNeverTouchesTheWeb = noClasses()
            .that().resideInAPackage("..ai_assistant_service.service..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.web..", "org.springframework.http..", "jakarta.servlet..");

    @ArchTest
    static final ArchRule entitiesRefuseBlindWrites = noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage("..ai_assistant_service.repository.entity..")
            .and().haveNameMatching("set[A-Z].*")
            .should().bePublic()
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule controllersLiveInTheWebLayer = noClasses()
            .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .should().resideOutsideOfPackage("..ai_assistant_service.web..");
}

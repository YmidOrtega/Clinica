package com.ClinicaDeYmid.contracting_service;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "com.ClinicaDeYmid.contracting_service", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule layersOnlyDependInward = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("..contracting_service.domain..")
            .layer("Application").definedBy("..contracting_service.application..")
            .layer("Infrastructure").definedBy("..contracting_service.infrastructure..")
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure");

    @ArchTest
    static final ArchRule domainIgnoresWebAndRemoteCalls = noClasses()
            .that().resideInAPackage("..contracting_service.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.web..", "org.springframework.http..", "feign..", "jakarta.servlet..",
                    "org.springframework.security..", "org.springframework.cloud..");

    @ArchTest
    static final ArchRule applicationIgnoresWebAndPersistenceDetails = noClasses()
            .that().resideInAPackage("..contracting_service.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.web..", "feign..", "jakarta.servlet..", "jakarta.persistence..", "org.hibernate..");
}

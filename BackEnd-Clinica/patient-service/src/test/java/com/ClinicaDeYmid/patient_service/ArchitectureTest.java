package com.ClinicaDeYmid.patient_service;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "com.ClinicaDeYmid.patient_service", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule layersOnlyDependInward = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("..patient_service.domain..")
            .layer("Application").definedBy("..patient_service.application..")
            .layer("Infrastructure").definedBy("..patient_service.infrastructure..")
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure");

    @ArchTest
    static final ArchRule domainIgnoresWebAndRemoteCalls = noClasses()
            .that().resideInAPackage("..patient_service.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.web..", "org.springframework.http..", "feign..", "jakarta.servlet..",
                    "org.springframework.security..", "org.springframework.cloud..");

    @ArchTest
    static final ArchRule applicationIgnoresWebAndPersistenceDetails = noClasses()
            .that().resideInAPackage("..patient_service.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.web..", "feign..", "jakarta.servlet..", "jakarta.persistence..", "org.hibernate..");
}

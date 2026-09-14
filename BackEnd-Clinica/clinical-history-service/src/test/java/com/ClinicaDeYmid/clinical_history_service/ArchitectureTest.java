package com.ClinicaDeYmid.clinical_history_service;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "com.ClinicaDeYmid.clinical_history_service", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule layersOnlyDependInward = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("..clinical_history_service.domain..")
            .layer("Application").definedBy("..clinical_history_service.application..")
            .layer("Infrastructure").definedBy("..clinical_history_service.infrastructure..")
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure");

    @ArchTest
    static final ArchRule domainIgnoresFrameworks = noClasses()
            .that().resideInAPackage("..clinical_history_service.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "org.apache.kafka..", "feign..", "jakarta.servlet..", "com.fasterxml..");

    @ArchTest
    static final ArchRule applicationIgnoresTransportDetails = noClasses()
            .that().resideInAPackage("..clinical_history_service.application..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "org.apache.kafka..", "feign..", "jakarta.servlet..");
}

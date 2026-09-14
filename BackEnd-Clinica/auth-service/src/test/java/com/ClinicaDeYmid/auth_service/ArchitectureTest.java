package com.ClinicaDeYmid.auth_service;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "com.ClinicaDeYmid.auth_service", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule layersOnlyDependInward = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("Domain").definedBy("..auth_service.domain..")
            .layer("Application").definedBy("..auth_service.application..")
            .layer("Infrastructure").definedBy("..auth_service.infrastructure..")
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure");

    @ArchTest
    static final ArchRule domainIgnoresWebSecurityAndRemoteCalls = noClasses()
            .that().resideInAPackage("..auth_service.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.web..", "org.springframework.http..", "jakarta.servlet..",
                    "org.springframework.security..", "org.springframework.cloud..", "org.springframework.vault..",
                    "org.bouncycastle..", "org.springframework.jdbc..");

    @ArchTest
    static final ArchRule domainPackagesDoNotDependOnEachOtherCyclically = slices().matching("..auth_service.domain.(*)..").should().beFreeOfCycles();
}

package com.ClinicaDeYmid.api_gateway;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "com.ClinicaDeYmid.api_gateway", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule layersOnlyDependInward = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("Domain").definedBy("..api_gateway.domain..")
            .layer("Infrastructure").definedBy("..api_gateway.infrastructure..")
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Infrastructure");

    @ArchTest
    static final ArchRule domainIsPlainJava = noClasses()
            .that().resideInAPackage("..api_gateway.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "io.lettuce..");
}

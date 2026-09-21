package com.ClinicaDeYmid.admissions_service;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "com.ClinicaDeYmid.admissions_service", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule layersOnlyDependInward = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("..admissions_service.domain..")
            .layer("Application").definedBy("..admissions_service.application..")
            .layer("Infrastructure").definedBy("..admissions_service.infrastructure..")
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure");

    @ArchTest
    static final ArchRule domainIgnoresWebAndRemoteCalls = noClasses()
            .that().resideInAPackage("..admissions_service.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.web..", "org.springframework.http..", "feign..", "jakarta.servlet..",
                    "org.springframework.security..", "org.springframework.cloud..");

    @ArchTest
    static final ArchRule applicationIgnoresWebAndPersistenceDetails = noClasses()
            .that().resideInAPackage("..admissions_service.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.web..", "feign..", "jakarta.servlet..", "jakarta.persistence..", "org.hibernate..");

    private static final Set<String> DELIBERATELY_PUBLIC = Set.of(
            "com.ClinicaDeYmid.admissions_service.infrastructure.web.ReceiptController.verifyPublicly",
            "com.ClinicaDeYmid.admissions_service.infrastructure.web.ReceiptController.sealKeys");

    private static final List<String> MAPPINGS = List.of(
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.PatchMapping",
            "org.springframework.web.bind.annotation.DeleteMapping",
            "org.springframework.web.bind.annotation.RequestMapping");

    @ArchTest
    static final ArchRule everyEndpointSaysWhoMayCallIt = methods()
            .that().areDeclaredInClassesThat().resideInAPackage("..admissions_service.infrastructure.web..")
            .should(declareTheirPermission());

    @ArchTest
    static final ArchRule nothingBringsBackLombok = noClasses()
            .that().resideInAPackage("..admissions_service..")
            .should().dependOnClassesThat().resideInAnyPackage("lombok..");

    private static ArchCondition<JavaMethod> declareTheirPermission() {
        return new ArchCondition<>("declare @PreAuthorize unless they are deliberately public") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean exposed = MAPPINGS.stream().anyMatch(mapping -> method.isAnnotatedWith(mapping));
                if (!exposed) {
                    return;
                }
                String name = method.getOwner().getName() + "." + method.getName();
                boolean guarded = method.isAnnotatedWith("org.springframework.security.access.prepost.PreAuthorize")
                        || DELIBERATELY_PUBLIC.contains(name);
                events.add(new SimpleConditionEvent(method, guarded,
                        name + (guarded ? " says who may call it" : " is exposed without @PreAuthorize")));
            }
        };
    }
}

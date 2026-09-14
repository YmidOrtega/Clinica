package com.ClinicaDeYmid.clinical_history_service.infrastructure.clients;

import com.ClinicaDeYmid.clinical_history_service.application.patient.PatientDirectory;
import com.ClinicaDeYmid.clinical_history_service.application.patient.PatientLookup;
import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReferences;
import com.ClinicaDeYmid.clinical_history_service.support.ClinicalTestProperties;
import com.ClinicaDeYmid.clinical_history_service.support.MySqlTestContainer;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainer.class)
class PatientRegistryFallbackIT {

    @RegisterExtension
    static WireMockExtension patientService = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @Autowired
    private PatientDirectory directory;

    @Autowired
    private PatientReferences references;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.patient-service.url", patientService::baseUrl);
        registry.add("clinica.clinical.patient-events.enabled", () -> false);
        registry.add("spring.kafka.admin.auto-create", () -> false);
        ClinicalTestProperties.register(registry);
    }

    @Test
    void loadsARegisteredPatientThatHasNotArrivedByEvents() {
        UUID uuid = UUID.randomUUID();
        patientService.stubFor(get(urlPathEqualTo("/api/v1/patients/" + uuid)).willReturn(okJson("""
                {"uuid": "%s", "version": 5, "document": {"type": "PASAPORTE", "number": "AB123456"},
                 "demographics": {"firstNames": "Luis", "lastNames": "Pérez", "birthDate": "1985-01-20", "sex": "MALE"},
                 "contact": {"mobile": "3001234567"}, "status": {"code": "ACTIVE"},
                 "affiliation": {"regime": "UNINSURED"}, "healthProvider": {"availability": "NOT_AFFILIATED"}}
                """.formatted(uuid))));

        PatientLookup lookup = directory.find(uuid);

        assertThat(lookup).isInstanceOf(PatientLookup.Found.class);
        assertThat(references.find(uuid)).hasValueSatisfying(reference -> assertThat(reference.version()).isEqualTo(5));
    }

    @Test
    void fallsBackToUnidentifiedPatients() {
        UUID uuid = UUID.randomUUID();
        patientService.stubFor(get(urlPathEqualTo("/api/v1/patients/" + uuid)).willReturn(aResponse().withStatus(404)));
        patientService.stubFor(get(urlPathEqualTo("/api/v1/unidentified-patients/" + uuid)).willReturn(okJson("""
                {"uuid": "%s", "version": 0, "code": "NN-2026-000009", "sex": "FEMALE", "estimatedBirthYear": 1995,
                 "description": "Mujer", "status": {"code": "UNIDENTIFIED"}}
                """.formatted(uuid))));

        assertThat(directory.find(uuid)).isInstanceOfSatisfying(PatientLookup.Found.class,
                found -> assertThat(found.patient()).isInstanceOf(PatientReference.Unidentified.class));
    }

    @Test
    void reportsUnknownAndUnreachablePatients() {
        UUID unknown = UUID.randomUUID();
        UUID unreachable = UUID.randomUUID();
        patientService.stubFor(get(urlPathEqualTo("/api/v1/patients/" + unknown)).willReturn(aResponse().withStatus(404)));
        patientService.stubFor(get(urlPathEqualTo("/api/v1/unidentified-patients/" + unknown)).willReturn(aResponse().withStatus(404)));
        patientService.stubFor(get(urlPathEqualTo("/api/v1/patients/" + unreachable)).willReturn(aResponse().withStatus(503)));

        assertThat(directory.find(unknown)).isEqualTo(new PatientLookup.NotFound());
        assertThat(directory.find(unreachable)).isEqualTo(new PatientLookup.Unavailable());
        assertThat(references.find(unreachable)).isEmpty();
    }
}

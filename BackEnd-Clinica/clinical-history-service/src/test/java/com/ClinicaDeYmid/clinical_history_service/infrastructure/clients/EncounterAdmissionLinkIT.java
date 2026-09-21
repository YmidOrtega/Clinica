package com.ClinicaDeYmid.clinical_history_service.infrastructure.clients;

import com.ClinicaDeYmid.clinical_history_service.application.encounter.EncounterCommands;
import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.ClinicalRole;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.Encounter;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.EncounterType;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(MySqlTestContainer.class)
class EncounterAdmissionLinkIT {

    @RegisterExtension
    static WireMockExtension services = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @Autowired
    private EncounterCommands encounters;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.patient-service.url", services::baseUrl);
        registry.add("spring.cloud.openfeign.client.config.admissions-service.url", services::baseUrl);
        registry.add("clinica.clinical.patient-events.enabled", () -> false);
        registry.add("spring.kafka.admin.auto-create", () -> false);
        ClinicalTestProperties.register(registry);
    }

    @Test
    void linksTheEncounterToAnEpisodeThatAdmissionsConfirms() {
        UUID patient = anActivePatient();
        UUID episode = UUID.randomUUID();
        services.stubFor(get(urlPathEqualTo("/api/v1/admissions/episodes/" + episode))
                .willReturn(okJson("{\"uuid\":\"" + episode + "\",\"number\":\"ADM-2026-000001\"}")));

        Encounter encounter = encounters.open(patient, EncounterType.EMERGENCY, episode, nurse());

        assertThat(encounter.admissionUuid()).isEqualTo(episode);
        assertThat(encounter.admissionVerified()).isTrue();
    }

    @Test
    void refusesAnEpisodeThatAdmissionsDoesNotKnow() {
        UUID patient = anActivePatient();
        UUID episode = UUID.randomUUID();
        services.stubFor(get(urlPathEqualTo("/api/v1/admissions/episodes/" + episode))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> encounters.open(patient, EncounterType.EMERGENCY, episode, nurse()))
                .isInstanceOf(ClinicalException.AdmissionNotFound.class);
    }

    @Test
    void opensUnverifiedWhenAdmissionsDoesNotAnswer() {
        UUID patient = anActivePatient();
        UUID episode = UUID.randomUUID();
        services.stubFor(get(urlPathEqualTo("/api/v1/admissions/episodes/" + episode))
                .willReturn(aResponse().withStatus(503)));

        Encounter encounter = encounters.open(patient, EncounterType.EMERGENCY, episode, nurse());

        assertThat(encounter.admissionUuid()).isEqualTo(episode);
        assertThat(encounter.admissionVerified()).isFalse();
    }

    @Test
    void anEncounterWithoutAnEpisodeNeverAsksAdmissions() {
        Encounter encounter = encounters.open(anActivePatient(), EncounterType.OUTPATIENT, null, nurse());

        assertThat(encounter.admissionUuid()).isNull();
        assertThat(encounter.admissionVerified()).isFalse();
    }

    private UUID anActivePatient() {
        UUID uuid = UUID.randomUUID();
        services.stubFor(get(urlPathEqualTo("/api/v1/patients/" + uuid)).willReturn(okJson("""
                {"uuid": "%s", "version": 1, "document": {"type": "CEDULA_DE_CIUDADANIA", "number": "1098765432"},
                 "demographics": {"firstNames": "Ana", "lastNames": "Restrepo", "birthDate": "1990-04-12", "sex": "FEMALE"},
                 "contact": {"mobile": "3001234567"}, "status": {"code": "ACTIVE"},
                 "affiliation": {"regime": "UNINSURED"}, "healthProvider": {"availability": "NOT_AFFILIATED"}}
                """.formatted(uuid))));
        return uuid;
    }

    private static Clinician nurse() {
        return new Clinician(UUID.randomUUID(), ClinicalRole.NURSE);
    }
}

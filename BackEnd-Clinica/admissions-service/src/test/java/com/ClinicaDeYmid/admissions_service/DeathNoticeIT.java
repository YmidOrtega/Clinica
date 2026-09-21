package com.ClinicaDeYmid.admissions_service;

import com.ClinicaDeYmid.admissions_service.application.CatalogueCommands;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import com.ClinicaDeYmid.admissions_service.domain.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.domain.Location;
import com.ClinicaDeYmid.admissions_service.domain.ServiceType;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReferences;
import com.ClinicaDeYmid.admissions_service.support.StubbedServices;
import com.ClinicaDeYmid.admissions_service.support.TestSequence;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeathNoticeIT extends IntegrationTest {

    private static final String EPISODES = "/api/v1/admissions/episodes";
    private static final String DEATH = """
            {"type":"DEATH","occurredAt":"%s","certificateNumber":"CD-2026-%04d"}""";

    @Autowired
    private CatalogueCommands catalogue;

    @Autowired
    private PatientReferences patients;

    @Test
    void tellsThePatientDirectoryAboutTheDeathAndMarksTheNoticeSent() throws Exception {
        UUID patient = aLocalPatient();
        directoryAnswers(patient, "ACTIVE");
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock
                .post(urlPathEqualTo("/api/v1/patients/" + patient + "/death"))
                .willReturn(okJson(registeredPayload(patient, 4, "DECEASED"))));
        String episode = anActiveEpisodeOf(patient);

        change("DOCTOR", post(EPISODES + "/" + episode + "/discharge"), 1, death())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.discharge.type").value("DEATH"))
                .andExpect(jsonPath("$.deathNotice.status").value("SENT"))
                .andExpect(jsonPath("$.deathNotice.detail").doesNotExist());

        StubbedServices.server().verify(postRequestedFor(urlPathEqualTo("/api/v1/patients/" + patient + "/death"))
                .withHeader("If-Match", equalTo("\"3\"")));
    }

    @Test
    void theEpisodeStillClosesWhenTheDirectoryDoesNotAnswerAndTheNoticeCanBeRetried() throws Exception {
        UUID patient = aLocalPatient();
        directoryAnswers(patient, "ACTIVE");
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock
                .post(urlPathEqualTo("/api/v1/patients/" + patient + "/death"))
                .willReturn(aResponse().withStatus(500)));
        String episode = anActiveEpisodeOf(patient);

        change("DOCTOR", post(EPISODES + "/" + episode + "/discharge"), 1, death())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("DISCHARGED"))
                .andExpect(jsonPath("$.deathNotice.status").value("PENDING"))
                .andExpect(jsonPath("$.deathNotice.detail").isNotEmpty());

        as("BILLING", org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get(EPISODES + "/pending-death-notice"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.uuid=='" + episode + "')]").exists());

        StubbedServices.reset();
        directoryAnswers(patient, "ACTIVE");
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock
                .post(urlPathEqualTo("/api/v1/patients/" + patient + "/death"))
                .willReturn(okJson(registeredPayload(patient, 4, "DECEASED"))));

        as("DOCTOR", post(EPISODES + "/" + episode + "/death-notice"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deathNotice.status").value("SENT"));

        as("BILLING", org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get(EPISODES + "/pending-death-notice"), null)
                .andExpect(jsonPath("$.content[?(@.uuid=='" + episode + "')]").doesNotExist());
    }

    @Test
    void anUnidentifiedPatientsDeathGoesToTheUnidentifiedRegistry() throws Exception {
        UUID patient = anUnidentifiedLocalPatient();
        StubbedServices.server().stubFor(get(urlPathEqualTo("/api/v1/patients/" + patient))
                .willReturn(aResponse().withStatus(404)));
        StubbedServices.server().stubFor(get(urlPathEqualTo("/api/v1/unidentified-patients/" + patient))
                .willReturn(okJson(unidentifiedPayload(patient, 2, "UNIDENTIFIED"))));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock
                .post(urlPathEqualTo("/api/v1/unidentified-patients/" + patient + "/death"))
                .willReturn(okJson(unidentifiedPayload(patient, 3, "DECEASED"))));
        String episode = anActiveEpisodeOf(patient);

        change("DOCTOR", post(EPISODES + "/" + episode + "/discharge"), 1, death())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deathNotice.status").value("SENT"));

        StubbedServices.server().verify(
                postRequestedFor(urlPathEqualTo("/api/v1/unidentified-patients/" + patient + "/death")));
    }

    @Test
    void aPatientAlreadyRecordedAsDeceasedNeedsNoSecondNotice() throws Exception {
        UUID patient = aLocalPatient();
        directoryAnswers(patient, "DECEASED");
        String episode = anActiveEpisodeOf(patient);

        change("DOCTOR", post(EPISODES + "/" + episode + "/discharge"), 1, death())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deathNotice.status").value("SENT"));

        StubbedServices.server().verify(0,
                postRequestedFor(urlPathEqualTo("/api/v1/patients/" + patient + "/death")));
    }

    @Test
    void aRejectionThatSaysTheDeathWasAlreadyRecordedCountsAsSent() throws Exception {
        UUID patient = aLocalPatient();
        directoryAnswers(patient, "ACTIVE");
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock
                .post(urlPathEqualTo("/api/v1/patients/" + patient + "/death"))
                .willReturn(aResponse().withStatus(422).withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"code\":\"PATIENT_INVALID_STATUS_TRANSITION\"}")));
        String episode = anActiveEpisodeOf(patient);

        change("DOCTOR", post(EPISODES + "/" + episode + "/discharge"), 1, death())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deathNotice.status").value("SENT"));
    }

    @Test
    void onlyEpisodesThatEndedInADeathCanBeReported() throws Exception {
        String episode = anActiveEpisodeOf(aLocalPatient());
        change("DOCTOR", post(EPISODES + "/" + episode + "/discharge"), 1, "{\"type\":\"MEDICAL\"}")
                .andExpect(status().isOk());

        as("DOCTOR", post(EPISODES + "/" + episode + "/death-notice"), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ADMISSION_WITHOUT_DEATH"));
    }

    private static String death() {
        return DEATH.formatted(Instant.now().minusSeconds(3600), TestSequence.next());
    }

    private void directoryAnswers(UUID patient, String status) {
        StubbedServices.server().stubFor(get(urlPathEqualTo("/api/v1/patients/" + patient))
                .willReturn(okJson(registeredPayload(patient, 3, status))));
    }

    private String anActiveEpisodeOf(UUID patient) throws Exception {
        String body = as("RECEPTIONIST", post(EPISODES), "{\"patientUuid\":\"" + patient
                + "\",\"configurationServiceUuid\":\"" + emergency() + "\",\"cause\":\"ILLNESS\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String uuid = JsonPath.read(body, "$.uuid");
        change("RECEPTIONIST", post(EPISODES + "/" + uuid + "/activation"), 0, null).andExpect(status().isOk());
        return uuid;
    }

    private UUID aLocalPatient() {
        UUID uuid = UUID.randomUUID();
        patients.saveIfNewer(new PatientReference.Registered(uuid, 1,
                new PatientReference.Document("CEDULA_DE_CIUDADANIA", "90" + TestSequence.next()),
                "Ana María", "Restrepo Gómez", LocalDate.of(1990, 4, 12), PatientReference.Sex.FEMALE,
                PatientReference.Registered.Status.ACTIVE, null, "CONTRIBUTORY", null));
        return uuid;
    }

    private UUID anUnidentifiedLocalPatient() {
        UUID uuid = UUID.randomUUID();
        patients.saveIfNewer(new PatientReference.Unidentified(uuid, 1, "NN-2026-" + TestSequence.next(),
                PatientReference.Sex.MALE, 1980, PatientReference.Unidentified.Status.UNIDENTIFIED, null, null));
        return uuid;
    }

    private static String registeredPayload(UUID uuid, long version, String status) {
        return """
                {"uuid":"%s","version":%d,
                 "document":{"type":"CEDULA_DE_CIUDADANIA","number":"1098765432"},
                 "demographics":{"firstNames":"Ana María","lastNames":"Restrepo Gómez","birthDate":"1990-04-12",
                                 "sex":"FEMALE"},
                 "status":{"code":"%s"%s},
                 "affiliation":{"regime":"CONTRIBUTORY","payerUuid":null}}"""
                .formatted(uuid, version, status, "DECEASED".equals(status) ? ",\"dateOfDeath\":\"2026-09-20\"" : "");
    }

    private static String unidentifiedPayload(UUID uuid, long version, String status) {
        return """
                {"uuid":"%s","version":%d,"code":"NN-2026-000042","sex":"MALE","estimatedBirthYear":1980,
                 "status":{"code":"%s"%s}}"""
                .formatted(uuid, version, status, "DECEASED".equals(status) ? ",\"dateOfDeath\":\"2026-09-20\"" : "");
    }

    private UUID emergency() {
        int index = TestSequence.next();
        ServiceType type = catalogue.defineServiceType("Urgencias muerte " + index, AdmissionKind.EMERGENCY);
        Location where = catalogue.defineLocation("Sede muerte " + index);
        ConfigurationService configured = catalogue.configure(type.uuid(), where.uuid());
        return configured.uuid();
    }
}

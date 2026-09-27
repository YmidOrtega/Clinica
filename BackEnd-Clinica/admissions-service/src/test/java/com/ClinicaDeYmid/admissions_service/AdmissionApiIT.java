package com.ClinicaDeYmid.admissions_service;

import com.ClinicaDeYmid.admissions_service.application.CatalogueCommands;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import com.ClinicaDeYmid.admissions_service.domain.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.domain.Location;
import com.ClinicaDeYmid.admissions_service.domain.ServiceType;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReferences;
import com.ClinicaDeYmid.admissions_service.support.JwtTestTokens;
import com.ClinicaDeYmid.admissions_service.support.TestSequence;
import com.ClinicaDeYmid.admissions_service.support.StubbedServices;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdmissionApiIT extends IntegrationTest {

    private static final String BASE = "/api/v1/admissions/episodes";
    @Autowired
    private CatalogueCommands catalogue;

    @Autowired
    private PatientReferences patients;

    @Test
    void admitsAKnownPatientIntoAnEmergencyEpisode() throws Exception {
        UUID patient = aLocalPatient("ACTIVE");

        as("RECEPTIONIST", post(BASE), registration(patient, emergency()))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.number").value(org.hamcrest.Matchers.matchesPattern("ADM-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.kind").value("EMERGENCY"))
                .andExpect(jsonPath("$.bedRequired").value(false))
                .andExpect(jsonPath("$.status.code").value("REGISTERED"))
                .andExpect(jsonPath("$.currentPhase.kind").value("EMERGENCY"))
                .andExpect(jsonPath("$.phases.length()").value(1));
    }

    @Test
    void registersTheUnidentifiedPatientInPatientServiceBeforeAdmitting() throws Exception {
        UUID assigned = UUID.randomUUID();
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/api/v1/unidentified-patients"))
                .willReturn(okJson(unidentifiedPayload(assigned, "NN-2026-000042"))));

        as("RECEPTIONIST", post(BASE + "/unidentified"), """
                {"sex":"MALE","estimatedBirthYear":1980,"description":"Hombre adulto sin documentos",
                 "configurationServiceUuid":"%s","cause":"ACCIDENT"}""".formatted(emergency()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientUuid").value(assigned.toString()))
                .andExpect(jsonPath("$.kind").value("EMERGENCY"));

        StubbedServices.server().verify(1, postRequestedForUnidentified());
    }

    @Test
    void refusesToAdmitWhenPatientServiceDoesNotAnswer() throws Exception {
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/api/v1/unidentified-patients"))
                .willReturn(aResponse().withStatus(500)));

        as("RECEPTIONIST", post(BASE + "/unidentified"), """
                {"sex":"FEMALE","estimatedBirthYear":1975,"configurationServiceUuid":"%s","cause":"ACCIDENT"}"""
                .formatted(emergency()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UNIDENTIFIED_PATIENT_NOT_REGISTERED"));
    }

    @Test
    void refusesToAdmitAPatientNobodyKnows() throws Exception {
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                com.github.tomakehurst.wiremock.client.WireMock.urlMatching("/api/v1/.*"))
                .willReturn(aResponse().withStatus(404)));

        as("RECEPTIONIST", post(BASE), registration(UUID.randomUUID(), emergency()))
                .andExpect(status().isNotFound());
    }

    @Test
    void refusesToAdmitADeceasedPatient() throws Exception {
        UUID patient = aLocalPatient("DECEASED");

        as("RECEPTIONIST", post(BASE), registration(patient, emergency()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PATIENT_NOT_ADMISSIBLE"));
    }

    @Test
    void movesTheEpisodeFromEmergencyToTheWardKeepingItsNumber() throws Exception {
        String body = as("RECEPTIONIST", post(BASE), registration(aLocalPatient("ACTIVE"), emergency()))
                .andReturn().getResponse().getContentAsString();
        String uuid = JsonPath.read(body, "$.uuid");
        String number = JsonPath.read(body, "$.number");

        change("RECEPTIONIST", post(BASE + "/" + uuid + "/activation"), 0, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("ACTIVE"));

        change("RECEPTIONIST", post(BASE + "/" + uuid + "/phase"), 1,
                "{\"configurationServiceUuid\":\"" + inpatient() + "\",\"reason\":\"Requiere hospitalización\","
                        + "\"bedUuid\":\"" + aBed() + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(number))
                .andExpect(jsonPath("$.kind").value("INPATIENT"))
                .andExpect(jsonPath("$.bedRequired").value(true))
                .andExpect(jsonPath("$.phases.length()").value(2))
                .andExpect(jsonPath("$.phases[0].endedAt").isNotEmpty());
    }

    @Test
    void dischargesAnActiveEpisodeAndKeepsItReadable() throws Exception {
        String uuid = anActiveEpisode();

        change("DOCTOR", post(BASE + "/" + uuid + "/discharge"), 1,
                "{\"type\":\"MEDICAL\",\"notes\":\"Paciente estable\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("DISCHARGED"))
                .andExpect(jsonPath("$.status.open").value(false))
                .andExpect(jsonPath("$.status.discharge.type").value("MEDICAL"))
                .andExpect(jsonPath("$.status.discharge.notes").value("Paciente estable"));

        as("BILLING", get(BASE + "/" + uuid), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("DISCHARGED"));
    }

    @Test
    void cancellingKeepsTheReasonAndDemandsOne() throws Exception {
        String uuid = anActiveEpisode();

        change("ADMIN", post(BASE + "/" + uuid + "/cancellation"), 1, "{\"reason\":\"  \"}")
                .andExpect(status().isBadRequest());

        change("ADMIN", post(BASE + "/" + uuid + "/cancellation"), 1, "{\"reason\":\"Se registró dos veces\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("CANCELLED"))
                .andExpect(jsonPath("$.status.reason").value("Se registró dos veces"));
    }

    @Test
    void findsEpisodesByNumberByPatientAndByDocumentWithoutPuttingThemInTheUrl() throws Exception {
        UUID patient = aLocalPatient("ACTIVE");
        String document = documentOf(patient);
        String body = as("RECEPTIONIST", post(BASE), registration(patient, emergency()))
                .andReturn().getResponse().getContentAsString();
        String number = JsonPath.read(body, "$.number");

        as("BILLING", post(BASE + "/search"), "{\"number\":\"" + number + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].patientUuid").value(patient.toString()))
                .andExpect(jsonPath("$.content[0].kind").value("EMERGENCY"))
                .andExpect(jsonPath("$.content[0].status").value("REGISTERED"))
                .andExpect(jsonPath("$.content[0].phases").doesNotExist());

        as("BILLING", post(BASE + "/search"), "{\"patientUuid\":\"" + patient + "\"}")
                .andExpect(jsonPath("$.content.length()").value(1));

        as("BILLING", post(BASE + "/search"),
                "{\"document\":{\"type\":\"CEDULA_DE_CIUDADANIA\",\"number\":\"" + document + "\"}}")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].number").value(number));

        as("BILLING", post(BASE + "/search"),
                "{\"document\":{\"type\":\"CEDULA_DE_CIUDADANIA\",\"number\":\"0000000\"}}")
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void theSearchFiltersByStatusKindAndServiceAndRefusesAnOversizedPage() throws Exception {
        UUID patient = aLocalPatient("ACTIVE");
        UUID service = emergency();
        String uuid = JsonPath.read(as("RECEPTIONIST", post(BASE), registration(patient, service))
                .andReturn().getResponse().getContentAsString(), "$.uuid");

        as("BILLING", post(BASE + "/search"),
                "{\"patientUuid\":\"" + patient + "\",\"status\":\"ACTIVE\"}")
                .andExpect(jsonPath("$.content.length()").value(0));

        change("RECEPTIONIST", post(BASE + "/" + uuid + "/activation"), 0, null).andExpect(status().isOk());

        as("BILLING", post(BASE + "/search"),
                "{\"patientUuid\":\"" + patient + "\",\"status\":\"ACTIVE\",\"kind\":\"EMERGENCY\","
                        + "\"configurationServiceUuid\":\"" + service + "\"}")
                .andExpect(jsonPath("$.content.length()").value(1));

        as("BILLING", post(BASE + "/search"),
                "{\"patientUuid\":\"" + patient + "\",\"kind\":\"INPATIENT\"}")
                .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post(BASE + "/search").param("size", "500")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer("BILLING")))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"NURSE", "DOCTOR", "MEDICAL_RECORDS", "BILLING"})
    void onlyReceptionAdmits(String role) throws Exception {
        as(role, post(BASE), registration(aLocalPatient("ACTIVE"), emergency()))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyDoctorsDischargeAndOnlyAdministrationCancels() throws Exception {
        String uuid = anActiveEpisode();

        change("NURSE", post(BASE + "/" + uuid + "/discharge"), 1, "{\"type\":\"MEDICAL\"}")
                .andExpect(status().isForbidden());
        change("RECEPTIONIST", post(BASE + "/" + uuid + "/cancellation"), 1, "{\"reason\":\"No\"}")
                .andExpect(status().isForbidden());
    }

    private String anActiveEpisode() throws Exception {
        String body = as("RECEPTIONIST", post(BASE), registration(aLocalPatient("ACTIVE"), emergency()))
                .andReturn().getResponse().getContentAsString();
        String uuid = JsonPath.read(body, "$.uuid");
        change("RECEPTIONIST", post(BASE + "/" + uuid + "/activation"), 0, null).andExpect(status().isOk());
        return uuid;
    }

    private static com.github.tomakehurst.wiremock.matching.RequestPatternBuilder postRequestedForUnidentified() {
        return com.github.tomakehurst.wiremock.client.WireMock
                .postRequestedFor(urlPathEqualTo("/api/v1/unidentified-patients"));
    }

    private static MockHttpServletRequestBuilder post(String path) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path);
    }

    private String registration(UUID patient, UUID service) {
        return "{\"patientUuid\":\"" + patient + "\",\"configurationServiceUuid\":\"" + service
                + "\",\"cause\":\"ILLNESS\"}";
    }

    private static String unidentifiedPayload(UUID uuid, String code) {
        return """
                {"uuid":"%s","version":0,"code":"%s","sex":"MALE","estimatedBirthYear":1980,
                 "status":{"code":"UNIDENTIFIED"}}""".formatted(uuid, code);
    }

    private final java.util.Map<UUID, String> documents = new java.util.HashMap<>();

    private String documentOf(UUID patient) {
        return documents.get(patient);
    }

    private UUID aLocalPatient(String status) {
        UUID uuid = UUID.randomUUID();
        String document = "20" + TestSequence.next();
        documents.put(uuid, document);
        patients.saveIfNewer(new PatientReference.Registered(uuid, 1,
                new PatientReference.Document("CEDULA_DE_CIUDADANIA", document),
                "Ana María", "Restrepo Gómez", LocalDate.of(1990, 4, 12), PatientReference.Sex.FEMALE,
                PatientReference.Registered.Status.valueOf(status),
                "DECEASED".equals(status) ? LocalDate.of(2026, 9, 19) : null, "CONTRIBUTORY", null));
        return uuid;
    }

    private String aBed() throws Exception {
        int index = TestSequence.next();
        String location = JsonPath.read(as("ADMIN", org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/admissions/catalogue/locations"),
                "{\"name\":\"Piso cama " + index + "\"}").andReturn().getResponse().getContentAsString(), "$.uuid");
        String room = JsonPath.read(as("ADMIN", org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/admissions/rooms"),
                "{\"name\":\"Hab " + index + "\",\"locationUuid\":\"" + location + "\",\"stayType\":\"GENERAL_WARD\"}")
                .andReturn().getResponse().getContentAsString(), "$.uuid");
        return JsonPath.read(as("ADMIN", org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/admissions/beds"),
                "{\"label\":\"Cama " + index + "\",\"roomUuid\":\"" + room + "\"}")
                .andReturn().getResponse().getContentAsString(), "$.uuid");
    }

    private UUID emergency() {
        return configured("Urgencias", AdmissionKind.EMERGENCY, "Piso 1");
    }

    private UUID inpatient() {
        return configured("Hospitalización", AdmissionKind.INPATIENT, "Piso 3");
    }

    private UUID configured(String service, AdmissionKind kind, String location) {
        int index = TestSequence.next();
        ServiceType type = catalogue.defineServiceType(service + " " + index, kind);
        Location where = catalogue.defineLocation(location + " " + index);
        ConfigurationService configured = catalogue.configure(type.uuid(), where.uuid());
        return configured.uuid();
    }

}

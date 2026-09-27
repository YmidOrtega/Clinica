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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CoverageApiIT extends IntegrationTest {

    private static final String BASE = "/api/v1/admissions/episodes";
    @Autowired
    private CatalogueCommands catalogue;

    @Autowired
    private PatientReferences patients;

    @Test
    void anEventContractInForceCoversTheAdmission() throws Exception {
        UUID payer = UUID.randomUUID();
        activeEventContract(payer, "CT-2026-001");

        admit("RECEPTIONIST", aPatient(payer), inpatient(), false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.coverage.status").value("COVERED"))
                .andExpect(jsonPath("$.coverage.contractNumber").value("CT-2026-001"))
                .andExpect(jsonPath("$.coverage.pending").value(false));
    }

    @Test
    void anInpatientAdmissionWithoutAContractIsBlocked() throws Exception {
        UUID payer = UUID.randomUUID();
        noContracts(payer);

        admit("RECEPTIONIST", aPatient(payer), inpatient(), false)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ADMISSION_WITHOUT_COVERAGE"));
    }

    @Test
    void theSameCaseInAnEmergencyIsAdmittedAndMarked() throws Exception {
        UUID payer = UUID.randomUUID();
        noContracts(payer);

        admit("RECEPTIONIST", aPatient(payer), emergency(), false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.coverage.status").value("NOT_COVERED"))
                .andExpect(jsonPath("$.coverage.pending").value(true))
                .andExpect(jsonPath("$.coverage.detail").exists());
    }

    @Test
    void whenContractingDoesNotAnswerNobodyIsBlockedButEverybodyIsMarked() throws Exception {
        UUID payer = UUID.randomUUID();
        StubbedServices.server().stubFor(get(urlPathEqualTo("/api/v1/contracts")).willReturn(aResponse().withStatus(500)));

        admit("RECEPTIONIST", aPatient(payer), inpatient(), false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.coverage.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.coverage.pending").value(true));
    }

    @Test
    void aCapitatedContractDemandsThePatientBeInThePopulation() throws Exception {
        UUID payer = UUID.randomUUID();
        capitatedContract(payer);
        StubbedServices.server().stubFor(get(urlPathEqualTo("/api/v1/capitated-members/coverage")).willReturn(okJson("[]")));

        admit("RECEPTIONIST", aPatient(payer), inpatient(), false)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("población")));
    }

    @Test
    void aCapitatedPatientInThePopulationIsCovered() throws Exception {
        UUID payer = UUID.randomUUID();
        UUID contract = capitatedContract(payer);
        StubbedServices.server().stubFor(get(urlPathEqualTo("/api/v1/capitated-members/coverage")).willReturn(okJson(
                "[{\"contractUuid\":\"" + contract + "\",\"contractNumber\":\"CT-CAP-1\",\"payerUuid\":\""
                        + payer + "\"}]")));

        admit("RECEPTIONIST", aPatient(payer), inpatient(), false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.coverage.status").value("COVERED"))
                .andExpect(jsonPath("$.coverage.contractNumber").value("CT-CAP-1"));
    }

    @Test
    void anAdministratorAdmitsWithoutCoverageButReceptionCannot() throws Exception {
        UUID payer = UUID.randomUUID();
        noContracts(payer);

        admit("RECEPTIONIST", aPatient(payer), inpatient(), true)
                .andExpect(status().isForbidden());

        admit("ADMIN", aPatient(payer), inpatient(), true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.coverage.status").value("NOT_COVERED"));
    }

    @Test
    void theMarkedEpisodesShowUpInThePendingPanel() throws Exception {
        UUID payer = UUID.randomUUID();
        noContracts(payer);
        admit("RECEPTIONIST", aPatient(payer), emergency(), false).andExpect(status().isCreated());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get(BASE + "/pending-coverage")
                        .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer("BILLING")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[0].coverage").value("NOT_COVERED"))
                .andExpect(jsonPath("$.page.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    private void activeEventContract(UUID payer, String number) {
        StubbedServices.server().stubFor(get(urlPathEqualTo("/api/v1/contracts"))
                .withQueryParam("inForceOn", com.github.tomakehurst.wiremock.client.WireMock.matching("\\d{4}-\\d{2}-\\d{2}"))
                .willReturn(okJson(
                "[{\"uuid\":\"" + UUID.randomUUID() + "\",\"number\":\"" + number + "\",\"modality\":\"EVENT\","
                        + "\"payerUuid\":\"" + payer + "\",\"status\":{\"code\":\"ACTIVE\"}}]")));
    }

    private UUID capitatedContract(UUID payer) {
        UUID contract = UUID.randomUUID();
        StubbedServices.server().stubFor(get(urlPathEqualTo("/api/v1/contracts")).willReturn(okJson(
                "[{\"uuid\":\"" + contract + "\",\"number\":\"CT-CAP-1\",\"modality\":\"CAPITATION\","
                        + "\"payerUuid\":\"" + payer + "\",\"status\":{\"code\":\"ACTIVE\"}}]")));
        return contract;
    }

    private void noContracts(UUID payer) {
        StubbedServices.server().stubFor(get(urlPathEqualTo("/api/v1/contracts")).willReturn(okJson("[]")));
    }

    private ResultActions admit(String role, UUID patient, UUID service, boolean override) throws Exception {
        String body = "{\"patientUuid\":\"" + patient + "\",\"configurationServiceUuid\":\"" + service
                + "\",\"cause\":\"ILLNESS\",\"overrideCoverage\":" + override + "}";
        return mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, JwtTestTokens.bearer(role))
                .content(body));
    }

    private UUID aPatient(UUID payer) {
        UUID uuid = UUID.randomUUID();
        patients.saveIfNewer(new PatientReference.Registered(uuid, 1,
                new PatientReference.Document("CEDULA_DE_CIUDADANIA", "30" + TestSequence.next()),
                "Ana María", "Restrepo Gómez", LocalDate.of(1990, 4, 12), PatientReference.Sex.FEMALE,
                PatientReference.Registered.Status.ACTIVE, null, "CONTRIBUTORY",
                payer == null ? null : payer.toString()));
        return uuid;
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

package com.ClinicaDeYmid.billing_service;

import com.ClinicaDeYmid.billing_service.application.EpisodeAccountProjection;
import com.ClinicaDeYmid.billing_service.domain.AdmissionKind;
import com.ClinicaDeYmid.billing_service.domain.AdmissionSnapshot;
import com.ClinicaDeYmid.billing_service.support.AdmissionEvents;
import com.ClinicaDeYmid.billing_service.support.StubbedServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SaleContextApiIT extends IntegrationTest {

    private static final String CONTEXT = "/api/v1/billing/sales/context/";
    private static final String PAYER = "7f3a1c2e-9b8d-4e6f-a5b4-c3d2e1f0a9b8";
    private static final String CONTRACT = "2b4c6d8e-0f1a-4b3c-9d5e-7f8a9b0c1d2e";
    private static final String PORTFOLIO_ITEM = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d";

    @Autowired
    private EpisodeAccountProjection projection;

    @Test
    void gathersThePatientThePhaseThePayerAndTheAuthorizationsInForce() throws Exception {
        Episode episode = anEpisode();
        admissionsKnows(episode, coverage("COVERED"));
        authorizations(episode, """
                [{"uuid":"%s","number":"AUT-778899","type":"HOSPITALIZATION","authorizedBy":"Nueva EPS",
                  "copayment":35000.00,"validFrom":"2026-09-25","validTo":"2026-10-25",
                  "authorizedItems":["%s"],"coversEverything":false,"status":"ACTIVE"},
                 {"uuid":"%s","number":"AUT-000001","type":"EMERGENCY_SERVICES","authorizedBy":"Nueva EPS",
                  "copayment":0,"validFrom":"2026-09-24","validTo":"2026-09-24",
                  "authorizedItems":[],"coversEverything":true,"status":"REVOKED"}]"""
                .formatted(UUID.randomUUID(), PORTFOLIO_ITEM, UUID.randomUUID()));
        patientRegistered(episode.patient());
        payerKnown();

        as("BILLING", get(CONTEXT + episode.number()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.warnings.length()").value(0))
                .andExpect(jsonPath("$.account.admissionNumber").value(episode.number()))
                .andExpect(jsonPath("$.account.status.code").value("OPEN"))
                .andExpect(jsonPath("$.episode.cause").value("TRAFFIC_ACCIDENT"))
                .andExpect(jsonPath("$.episode.currentPhase.configurationServiceName").value("Hospitalización adultos"))
                .andExpect(jsonPath("$.episode.attending.registrationNumber").value("RM-12345"))
                .andExpect(jsonPath("$.patient.identified").value(true))
                .andExpect(jsonPath("$.patient.fullName").value("Ana María Restrepo Gómez"))
                .andExpect(jsonPath("$.patient.documentNumber").value("1098765432"))
                .andExpect(jsonPath("$.patient.healthRegime").value("CONTRIBUTORY"))
                .andExpect(jsonPath("$.payer.name").value("Nueva EPS S.A."))
                .andExpect(jsonPath("$.payer.contractNumber").value("CT-2026-001"))
                .andExpect(jsonPath("$.payer.coverage").value("COVERED"))
                .andExpect(jsonPath("$.authorizations.length()").value(1))
                .andExpect(jsonPath("$.authorizations[0].number").value("AUT-778899"))
                .andExpect(jsonPath("$.authorizations[0].copayment").value(35000.00))
                .andExpect(jsonPath("$.authorizations[0].authorizedItems[0]").value(PORTFOLIO_ITEM));
    }

    @Test
    void anUnidentifiedPatientStillGetsItsContext() throws Exception {
        Episode episode = anEpisode();
        admissionsKnows(episode, null);
        authorizations(episode, "[]");
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/patients/" + episode.patient())).willReturn(notFound()));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/unidentified-patients/" + episode.patient())).willReturn(okJson("""
                {"uuid":"%s","code":"NN-2026-000042","sex":"MALE","estimatedBirthYear":1980}"""
                .formatted(episode.patient()))));

        as("BILLING", get(CONTEXT + episode.number()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.patient.identified").value(false))
                .andExpect(jsonPath("$.patient.code").value("NN-2026-000042"))
                .andExpect(jsonPath("$.payer").doesNotExist());
        StubbedServices.server().verify(0, com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                urlPathMatching("/api/v1/payers/.*")));
    }

    @Test
    void withoutThePatientDirectoryOrContractingTheContextArrivesIncompleteButUsable() throws Exception {
        Episode episode = anEpisode();
        admissionsKnows(episode, coverage("COVERED"));
        authorizations(episode, "[]");
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathMatching("/api/v1/(patients|payers)/.*")).willReturn(serverError()));

        as("BILLING", get(CONTEXT + episode.number()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.patient").doesNotExist())
                .andExpect(jsonPath("$.payer.uuid").value(PAYER))
                .andExpect(jsonPath("$.payer.name").doesNotExist())
                .andExpect(jsonPath("$.warnings.length()").value(2));
    }

    @Test
    void withoutAdmissionsThereIsNoContext() throws Exception {
        Episode episode = anEpisode();
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(anyUrl())
                .willReturn(aResponse().withFixedDelay(3500)));

        as("BILLING", get(CONTEXT + episode.number()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ADMISSIONS_UNAVAILABLE"));
    }

    @Test
    void anEpisodeAdmissionsNoLongerKnowsIsReported() throws Exception {
        Episode episode = anEpisode();
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/admissions/episodes/" + episode.uuid())).willReturn(notFound()));

        as("BILLING", get(CONTEXT + episode.number()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EPISODE_UNKNOWN_TO_ADMISSIONS"));
    }

    @Test
    void anAdmissionNumberWithoutAccountIsNotFound() throws Exception {
        as("BILLING", get(CONTEXT + "ADM-2026-999998"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "DOCTOR", "NURSE"})
    void onlyBillingPreparesASale(String role) throws Exception {
        as(role, get(CONTEXT + "ADM-2026-999998")).andExpect(status().isForbidden());
    }

    private Episode anEpisode() {
        Episode episode = new Episode(UUID.randomUUID(), AdmissionEvents.nextNumber(), UUID.randomUUID());
        projection.follow(new AdmissionSnapshot(episode.uuid(), episode.number(), 1, episode.patient(),
                AdmissionKind.INPATIENT, AdmissionSnapshot.Status.ACTIVE, UUID.randomUUID(),
                Instant.parse("2026-09-25T13:00:00Z"), null, null));
        return episode;
    }

    private static String coverage(String status) {
        return """
                {"status":"%s","contractUuid":"%s","contractNumber":"CT-2026-001","payerUuid":"%s",
                 "detail":null,"checkedAt":"2026-09-25T13:00:00Z","pending":false}""".formatted(status, CONTRACT, PAYER);
    }

    private static void admissionsKnows(Episode episode, String coverage) {
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/admissions/episodes/" + episode.uuid())).willReturn(okJson("""
                {"uuid":"%s","number":"%s","patientUuid":"%s","cause":"TRAFFIC_ACCIDENT","kind":"INPATIENT",
                 "bedRequired":true,"status":{"code":"ACTIVE","open":true},
                 "currentPhase":{"uuid":"%s","kind":"INPATIENT","configurationServiceUuid":"%s",
                   "configurationServiceName":"Hospitalización adultos","startedAt":"2026-09-25T13:00:00Z",
                   "current":true,"bedRequired":true},
                 "phases":[],"coverage":%s,
                 "attending":{"practitionerUuid":"%s","fullName":"Carlos Pérez","registrationNumber":"RM-12345"}}"""
                .formatted(episode.uuid(), episode.number(), episode.patient(), UUID.randomUUID(), UUID.randomUUID(),
                        coverage == null ? "null" : coverage, UUID.randomUUID()))));
    }

    private static void authorizations(Episode episode, String body) {
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/admissions/episodes/" + episode.uuid() + "/authorizations"))
                .willReturn(okJson(body)));
    }

    private static void patientRegistered(UUID patient) {
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/patients/" + patient)).willReturn(okJson("""
                {"uuid":"%s","version":3,"document":{"type":"CEDULA_DE_CIUDADANIA","number":"1098765432"},
                 "demographics":{"firstNames":"Ana María","lastNames":"Restrepo Gómez","birthDate":"1990-04-12",
                   "sex":"FEMALE"},
                 "status":{"code":"ACTIVE"},"affiliation":{"regime":"CONTRIBUTORY","payerUuid":"%s"}}"""
                .formatted(patient, PAYER))));
    }

    private static void payerKnown() {
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/payers/" + PAYER)).willReturn(okJson("""
                {"uuid":"%s","version":1,"socialReason":"Nueva EPS S.A.","nit":"900156264-2","type":"EPS",
                 "typeLabel":"EPS","status":{"code":"ACTIVE"}}""".formatted(PAYER))));
    }

    private record Episode(UUID uuid, String number, UUID patient) {
    }
}

package com.ClinicaDeYmid.billing_service;

import com.ClinicaDeYmid.billing_service.application.EpisodeAccountProjection;
import com.ClinicaDeYmid.billing_service.domain.AdmissionKind;
import com.ClinicaDeYmid.billing_service.domain.AdmissionSnapshot;
import com.ClinicaDeYmid.billing_service.support.AdmissionEvents;
import com.ClinicaDeYmid.billing_service.support.StubbedServices;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SaleApiIT extends IntegrationTest {

    private static final String SALES = "/api/v1/billing/sales";
    private static final String CONSULTATION = "2c1b0a9f-8e7d-4c6b-9a5f-4e3d2c1b0a9f";
    private static final String RETIRED_XRAY = "3d2c1b0a-9f8e-4d7c-8b6a-5f4e3d2c1b0a";
    private static final String TODAY = LocalDate.now(ZoneId.of("America/Bogota")).toString();

    @Autowired
    private EpisodeAccountProjection projection;

    @Test
    void opensASaleWithTheServicesAlreadyAuthorized() throws Exception {
        Episode episode = anEpisode(AdmissionSnapshot.Status.ACTIVE);
        admissionsAuthorizes(episode, CONSULTATION, RETIRED_XRAY);
        portfolioItem(CONSULTATION, "890201", "Consulta de primera vez por medicina general", true);
        portfolioItem(RETIRED_XRAY, "871121", "Radiografía de tórax", false);

        as("BILLING", post(SALES), opening(episode, null))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.number").value(episode.number() + "-V01"))
                .andExpect(jsonPath("$.type").value("NON_SURGICAL"))
                .andExpect(jsonPath("$.status.code").value("DRAFT"))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].cupsCode").value("890201"))
                .andExpect(jsonPath("$.lines[0].origin.code").value("AUTHORIZED"))
                .andExpect(jsonPath("$.lines[0].origin.authorizationNumber").value("AUT-778899"))
                .andExpect(jsonPath("$.lines[0].serviceDate").value(TODAY))
                .andExpect(jsonPath("$.notes[0]").value(org.hamcrest.Matchers.containsString("871121")));

        as("BILLING", post(SALES), opening(episode, false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(episode.number() + "-V02"))
                .andExpect(jsonPath("$.lines.length()").value(0));

        as("BILLING", get("/api/v1/billing/accounts/" + episode.number() + "/sales"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void withoutAdmissionsTheAuthorizedServicesCannotBePreloaded() throws Exception {
        Episode episode = anEpisode(AdmissionSnapshot.Status.ACTIVE);
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathMatching("/api/v1/admissions/.*")).willReturn(serverError()));

        as("BILLING", post(SALES), opening(episode, true))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ADMISSIONS_UNAVAILABLE"));
        as("BILLING", post(SALES), opening(episode, false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(episode.number() + "-V01"));
    }

    @Test
    void walksASaleFromDraftToConfirmed() throws Exception {
        String sale = emptySale(anEpisode(AdmissionSnapshot.Status.ACTIVE));
        portfolioSearch("890201", item(CONSULTATION, "890201", "Consulta de primera vez por medicina general", true));

        String body = change("BILLING", post(SALES + "/" + sale + "/lines"), 0,
                "{\"cupsCode\":\"890201\",\"quantity\":2}")
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.lines[0].description").value("Consulta de primera vez por medicina general"))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.lines[0].origin.code").value("MANUAL"))
                .andReturn().getResponse().getContentAsString();
        String line = JsonPath.read(body, "$.lines[0].uuid");
        portfolioItem(CONSULTATION, "890201", "Consulta de primera vez por medicina general", true);
        change("BILLING", post(SALES + "/" + sale + "/lines"), 1,
                "{\"portfolioItemUuid\":\"" + CONSULTATION + "\",\"quantity\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeLines").value(2));

        change("BILLING", post(SALES + "/" + sale + "/lines/" + line + "/removal"), 2, "{\"reason\":\"Duplicada\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeLines").value(1))
                .andExpect(jsonPath("$.lines[0].removed").value(true));

        change("BILLING", post(SALES + "/" + sale + "/confirmation"), 3, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("CONFIRMED"));
        change("BILLING", post(SALES + "/" + sale + "/lines"), 4, "{\"cupsCode\":\"890201\",\"quantity\":1}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SALE_NOT_EDITABLE"));

        change("BILLING", post(SALES + "/" + sale + "/cancellation"), 4, "{\"reason\":\"El paciente se retiró\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("CANCELLED"))
                .andExpect(jsonPath("$.status.reason").value("El paciente se retiró"));
    }

    @Test
    void refusesServicesTheClinicDoesNotOfferOrCannotTellApart() throws Exception {
        String sale = emptySale(anEpisode(AdmissionSnapshot.Status.ACTIVE));
        portfolioSearch("871121", item(RETIRED_XRAY, "871121", "Radiografía de tórax", false));
        portfolioSearch("890201", item(CONSULTATION, "890201", "Consulta general", true) + ","
                + item(UUID.randomUUID().toString(), "890201", "Consulta general nocturna", true));
        portfolioSearch("999999", "");

        change("BILLING", post(SALES + "/" + sale + "/lines"), 0, "{\"cupsCode\":\"871121\",\"quantity\":1}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SERVICE_NOT_OFFERED"));
        change("BILLING", post(SALES + "/" + sale + "/lines"), 0, "{\"cupsCode\":\"890201\",\"quantity\":1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AMBIGUOUS_SERVICE"));
        change("BILLING", post(SALES + "/" + sale + "/lines"), 0, "{\"cupsCode\":\"999999\",\"quantity\":1}")
                .andExpect(status().isUnprocessableEntity());
        change("BILLING", post(SALES + "/" + sale + "/lines"), 0, "{\"quantity\":1}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void withoutContractingNoServiceIsCharged() throws Exception {
        String sale = emptySale(anEpisode(AdmissionSnapshot.Status.ACTIVE));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(
                urlPathEqualTo("/api/v1/portfolio-items/search")).willReturn(serverError()));

        change("BILLING", post(SALES + "/" + sale + "/lines"), 0, "{\"cupsCode\":\"890201\",\"quantity\":1}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CONTRACTING_UNAVAILABLE"));
    }

    @Test
    void aCancelledAdmissionTakesNoSale() throws Exception {
        Episode episode = anEpisode(AdmissionSnapshot.Status.CANCELLED);

        as("BILLING", post(SALES), opening(episode, false))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ACCOUNT_CLOSED_FOR_CHARGES"));
    }

    @Test
    void anEmptySaleCannotBeConfirmedAndEveryChangeNeedsTheVersion() throws Exception {
        String sale = emptySale(anEpisode(AdmissionSnapshot.Status.ACTIVE));

        change("BILLING", post(SALES + "/" + sale + "/confirmation"), 0, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SALE_WITHOUT_LINES"));
        as("BILLING", post(SALES + "/" + sale + "/confirmation"))
                .andExpect(status().isPreconditionRequired());
        change("BILLING", post(SALES + "/" + sale + "/cancellation"), 7, "{\"reason\":\"x\"}")
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void twoPeopleChargingTheSameVersionDoNotOverwriteEachOther() throws Exception {
        String sale = emptySale(anEpisode(AdmissionSnapshot.Status.ACTIVE));
        portfolioItem(CONSULTATION, "890201", "Consulta general", true);
        String line = "{\"portfolioItemUuid\":\"" + CONSULTATION + "\",\"quantity\":1}";

        change("BILLING", post(SALES + "/" + sale + "/lines"), 0, line).andExpect(status().isOk());
        change("BILLING", post(SALES + "/" + sale + "/lines"), 0, line)
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_MISMATCH"));
    }

    @Test
    void recordsWhoChargedEachLine() throws Exception {
        String sale = emptySale(anEpisode(AdmissionSnapshot.Status.ACTIVE));
        portfolioItem(CONSULTATION, "890201", "Consulta general", true);

        change("BILLING", post(SALES + "/" + sale + "/lines"), 0,
                "{\"portfolioItemUuid\":\"" + CONSULTATION + "\",\"quantity\":1}").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("""
                SELECT l.created_by FROM sale_lines l JOIN sales s ON s.id = l.sale_id WHERE s.uuid = ?""",
                String.class, sale)).isEqualTo("00000000-0000-4000-8000-000000000008");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM billing_history.sale_lines_aud a JOIN sale_lines l ON l.id = a.id
                JOIN sales s ON s.id = l.sale_id WHERE s.uuid = ?""", Integer.class, sale)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "DOCTOR", "NURSE"})
    void onlyBillingSells(String role) throws Exception {
        Episode episode = anEpisode(AdmissionSnapshot.Status.ACTIVE);

        as(role, post(SALES), opening(episode, false)).andExpect(status().isForbidden());
    }

    private String emptySale(Episode episode) throws Exception {
        String body = as("BILLING", post(SALES), opening(episode, false))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.uuid");
    }

    private Episode anEpisode(AdmissionSnapshot.Status status) {
        Episode episode = new Episode(UUID.randomUUID(), AdmissionEvents.nextNumber());
        projection.follow(new AdmissionSnapshot(episode.uuid(), episode.number(), 1, UUID.randomUUID(),
                AdmissionKind.OUTPATIENT, status, UUID.randomUUID(), Instant.parse("2026-09-01T13:00:00Z"), null,
                status == AdmissionSnapshot.Status.CANCELLED ? "Duplicado" : null));
        return episode;
    }

    private static String opening(Episode episode, Boolean preload) {
        return "{\"admissionNumber\":\"" + episode.number() + "\",\"type\":\"NON_SURGICAL\""
                + (preload == null ? "" : ",\"preloadAuthorized\":" + preload) + "}";
    }

    private static void admissionsAuthorizes(Episode episode, String... items) {
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/admissions/episodes/" + episode.uuid())).willReturn(okJson("""
                {"uuid":"%s","number":"%s","patientUuid":"%s","cause":"ILLNESS","kind":"OUTPATIENT",
                 "status":{"code":"ACTIVE"},"coverage":null}""".formatted(episode.uuid(), episode.number(),
                UUID.randomUUID()))));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/admissions/episodes/" + episode.uuid() + "/authorizations")).willReturn(okJson("""
                [{"uuid":"%s","number":"AUT-778899","type":"AMBULATORY_SERVICES","authorizedBy":"Nueva EPS",
                  "copayment":0,"validFrom":"2026-09-01","validTo":"2026-12-31","authorizedItems":["%s"],
                  "coversEverything":false,"status":"ACTIVE"}]"""
                .formatted(UUID.randomUUID(), String.join("\",\"", items)))));
    }

    private static String item(String uuid, String cups, String name, boolean offered) {
        return """
                {"uuid":"%s","version":0,"cupsCode":"%s","clinicCode":null,"name":"%s","category":"CONSULTATION",
                 "status":{"code":"%s","offered":%s}}""".formatted(uuid, cups, name, offered ? "ACTIVE" : "INACTIVE",
                offered);
    }

    private static void portfolioItem(String uuid, String cups, String name, boolean offered) {
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/portfolio-items/" + uuid)).willReturn(okJson(item(uuid, cups, name, offered))));
    }

    private static void portfolioSearch(String cups, String items) {
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(
                        urlPathEqualTo("/api/v1/portfolio-items/search"))
                .withRequestBody(equalToJson("{\"cupsCode\":\"" + cups + "\"}"))
                .willReturn(okJson("{\"content\":[" + items + "],\"page\":{\"size\":10,\"number\":0}}")));
    }

    private record Episode(UUID uuid, String number) {
    }
}

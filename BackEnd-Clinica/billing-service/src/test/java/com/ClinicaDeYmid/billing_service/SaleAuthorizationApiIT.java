package com.ClinicaDeYmid.billing_service;

import com.ClinicaDeYmid.billing_service.application.EpisodeAccountProjection;
import com.ClinicaDeYmid.billing_service.domain.AdmissionKind;
import com.ClinicaDeYmid.billing_service.domain.AdmissionSnapshot;
import com.ClinicaDeYmid.billing_service.support.AdmissionEvents;
import com.ClinicaDeYmid.billing_service.support.StubbedServices;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SaleAuthorizationApiIT extends IntegrationTest {

    private static final String SALES = "/api/v1/billing/sales";
    private static final String MRI = "8e7d6c5b-4a3f-4e2d-9c1b-0a9f8e7d6c5b";
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));

    @Autowired
    private EpisodeAccountProjection projection;

    @Test
    void aServiceThatNeedsAuthorizationCannotBeConfirmedWithoutIt() throws Exception {
        String sale = saleFor(phases("INPATIENT", Instant.now().minusSeconds(86_400), null), "[]");

        as("BILLING", get(SALES + "/" + sale + "/price-preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.unauthorizedCups[0]").value("883101"))
                .andExpect(jsonPath("$.lines[0].authorization").value("MISSING"));
        change("BILLING", post(SALES + "/" + sale + "/confirmation"), 1, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SALE_HAS_UNAUTHORIZED_LINES"));
    }

    @Test
    void anAuthorizationOfTheEpisodeThatListsTheServiceLetsItThrough() throws Exception {
        String sale = saleFor(phases("INPATIENT", Instant.now().minusSeconds(86_400), null), """
                [{"uuid":"%s","number":"AUT-4455","type":"SPECIALIZED_SERVICES","authorizedBy":"Nueva EPS",
                  "copayment":0,"validFrom":"%s","validTo":"%s","authorizedItems":["%s"],
                  "coversEverything":false,"status":"ACTIVE"}]"""
                .formatted(UUID.randomUUID(), TODAY.minusDays(3), TODAY.plusDays(30), MRI));

        change("BILLING", post(SALES + "/" + sale + "/confirmation"), 1, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("CONFIRMED"));
    }

    @Test
    void whatIsDoneWhileTheEpisodeIsInTheEmergencyRoomNeedsNoAuthorization() throws Exception {
        String sale = saleFor(phases("EMERGENCY", Instant.now().minusSeconds(3_600), null), "[]");

        as("BILLING", get(SALES + "/" + sale + "/price-preview"))
                .andExpect(jsonPath("$.lines[0].authorization").value("EMERGENCY_EXEMPT"))
                .andExpect(jsonPath("$.complete").value(true));
        change("BILLING", post(SALES + "/" + sale + "/confirmation"), 1, null)
                .andExpect(status().isOk());
    }

    private String saleFor(String phases, String authorizations) throws Exception {
        UUID admission = UUID.randomUUID();
        UUID contract = UUID.randomUUID();
        String number = AdmissionEvents.nextNumber();
        projection.follow(new AdmissionSnapshot(admission, number, 1, UUID.randomUUID(), AdmissionKind.INPATIENT,
                AdmissionSnapshot.Status.ACTIVE, UUID.randomUUID(), Instant.parse("2026-09-01T13:00:00Z"), null, null));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/admissions/episodes/" + admission)).willReturn(okJson("""
                {"uuid":"%s","number":"%s","patientUuid":"%s","kind":"INPATIENT","status":{"code":"ACTIVE"},
                 "phases":%s,
                 "coverage":{"status":"COVERED","contractUuid":"%s","contractNumber":"CT-9","payerUuid":"%s"}}"""
                .formatted(admission, number, UUID.randomUUID(), phases, contract, UUID.randomUUID()))));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/admissions/episodes/" + admission + "/authorizations"))
                .willReturn(okJson(authorizations)));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/portfolio-items/" + MRI)).willReturn(okJson("""
                {"uuid":"%s","cupsCode":"883101","name":"Resonancia magnética de cerebro",
                 "status":{"code":"ACTIVE","offered":true}}""".formatted(MRI))));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(
                urlPathEqualTo("/api/v1/price-quotes")).willReturn(okJson("""
                {"contractUuid":"%s","contractNumber":"CT-9","payerUuid":"%s","services":[{"cupsCode":"883101",
                 "quantity":1,"unitPrice":350000.00,"lineTotal":350000.00,"origin":"TARIFF_MANUAL",
                 "referenceCode":"ISS2001","authorizationRequired":true}],"packages":[]}"""
                .formatted(contract, UUID.randomUUID()))));
        String opened = as("BILLING", post(SALES), "{\"admissionNumber\":\"" + number
                + "\",\"type\":\"NON_SURGICAL\",\"preloadAuthorized\":false}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String uuid = JsonPath.read(opened, "$.uuid");
        change("BILLING", post(SALES + "/" + uuid + "/lines"), 0,
                "{\"portfolioItemUuid\":\"" + MRI + "\",\"quantity\":1}").andExpect(status().isOk());
        return uuid;
    }

    private static String phases(String kind, Instant startedAt, Instant endedAt) {
        return "[{\"kind\":\"" + kind + "\",\"startedAt\":\"" + startedAt + "\""
                + (endedAt == null ? "" : ",\"endedAt\":\"" + endedAt + "\"") + ",\"current\":true}]";
    }
}

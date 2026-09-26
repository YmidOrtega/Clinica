package com.ClinicaDeYmid.billing_service;

import com.ClinicaDeYmid.billing_service.application.EpisodeAccountProjection;
import com.ClinicaDeYmid.billing_service.domain.AdmissionKind;
import com.ClinicaDeYmid.billing_service.domain.AdmissionSnapshot;
import com.ClinicaDeYmid.billing_service.support.AdmissionEvents;
import com.ClinicaDeYmid.billing_service.support.JwtTestTokens;
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

class AccountSummaryApiIT extends IntegrationTest {

    private static final String SALES = "/api/v1/billing/sales";
    private static final String ACCOUNTS = "/api/v1/billing/accounts/";
    private static final String CONSULTATION = "2c1b0a9f-8e7d-4c6b-9a5f-4e3d2c1b0a9f";
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));

    @Autowired
    private EpisodeAccountProjection projection;

    @Test
    void anOutpatientEpisodeShowsOneUnitPerSaleAndTheCopaymentOnce() throws Exception {
        Episode episode = outpatient();
        String first = confirmedSale(episode);
        String second = confirmedSale(episode);

        as("BILLING", get(ACCOUNTS + episode.number() + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.units.length()").value(2))
                .andExpect(jsonPath("$.units[0].kind").value("SALE"))
                .andExpect(jsonPath("$.units[0].ready").value(true))
                .andExpect(jsonPath("$.units[0].patientShare").value(35000.00))
                .andExpect(jsonPath("$.units[0].copayments[0].authorizationNumber").value("AUT-778899"))
                .andExpect(jsonPath("$.units[1].patientShare").value(0))
                .andExpect(jsonPath("$.total").value(90000.00))
                .andExpect(jsonPath("$.payerShare").value(55000.00));

        changeWithToken(JwtTestTokens.bearerWithoutSecondFactor("BILLING"),
                post(ACCOUNTS + episode.number() + "/patient-share-adjustments"), 0,
                "{\"saleUuid\":\"" + second + "\",\"amount\":4000,\"reason\":\"Cuota moderadora de la segunda consulta\"}")
                .andExpect(status().isUnauthorized());
        as("BILLING", post(ACCOUNTS + episode.number() + "/patient-share-adjustments"),
                "{\"saleUuid\":\"" + second + "\",\"amount\":4000,\"reason\":\"Cuota moderadora de la segunda consulta\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdBy").value("00000000-0000-4000-8000-000000000008"));
        as("BILLING", post(ACCOUNTS + episode.number() + "/patient-share-adjustments"),
                "{\"amount\":4000,\"reason\":\"Sin venta\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NOT_A_BILLABLE_UNIT"));
        as("RECEPTIONIST", post(ACCOUNTS + episode.number() + "/patient-share-adjustments"),
                "{\"saleUuid\":\"" + first + "\",\"amount\":0,\"reason\":\"x\"}")
                .andExpect(status().isForbidden());

        as("BILLING", get(ACCOUNTS + episode.number() + "/summary"))
                .andExpect(jsonPath("$.units[1].shareSource").value("ADJUSTED"))
                .andExpect(jsonPath("$.units[1].patientShare").value(4000.00))
                .andExpect(jsonPath("$.units[1].adjustment.reason").value("Cuota moderadora de la segunda consulta"))
                .andExpect(jsonPath("$.patientShare").value(39000.00));
    }

    private String confirmedSale(Episode episode) throws Exception {
        String opened = as("BILLING", post(SALES), "{\"admissionNumber\":\"" + episode.number()
                + "\",\"type\":\"NON_SURGICAL\",\"preloadAuthorized\":false}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String sale = JsonPath.read(opened, "$.uuid");
        change("BILLING", post(SALES + "/" + sale + "/lines"), 0,
                "{\"portfolioItemUuid\":\"" + CONSULTATION + "\",\"quantity\":1}").andExpect(status().isOk());
        change("BILLING", post(SALES + "/" + sale + "/confirmation"), 1, null).andExpect(status().isOk());
        return sale;
    }

    private Episode outpatient() {
        UUID admission = UUID.randomUUID();
        UUID contract = UUID.randomUUID();
        String number = AdmissionEvents.nextNumber();
        projection.follow(new AdmissionSnapshot(admission, number, 1, UUID.randomUUID(), AdmissionKind.OUTPATIENT,
                AdmissionSnapshot.Status.ACTIVE, UUID.randomUUID(), Instant.parse("2026-09-01T13:00:00Z"), null, null));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/admissions/episodes/" + admission)).willReturn(okJson("""
                {"uuid":"%s","number":"%s","patientUuid":"%s","kind":"OUTPATIENT","status":{"code":"ACTIVE"},
                 "coverage":{"status":"COVERED","contractUuid":"%s","contractNumber":"CT-1","payerUuid":"%s"}}"""
                .formatted(admission, number, UUID.randomUUID(), contract, UUID.randomUUID()))));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/admissions/episodes/" + admission + "/authorizations")).willReturn(okJson("""
                [{"uuid":"%s","number":"AUT-778899","type":"AMBULATORY_SERVICES","authorizedBy":"Nueva EPS",
                  "copayment":35000,"validFrom":"2026-09-01","validTo":"%s","authorizedItems":["%s"],
                  "coversEverything":false,"status":"ACTIVE"}]"""
                .formatted(UUID.randomUUID(), TODAY.plusDays(30), CONSULTATION))));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/portfolio-items/" + CONSULTATION)).willReturn(okJson("""
                {"uuid":"%s","cupsCode":"890201","name":"Consulta","status":{"code":"ACTIVE","offered":true}}"""
                .formatted(CONSULTATION))));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(
                urlPathEqualTo("/api/v1/price-quotes")).willReturn(okJson("""
                {"contractNumber":"CT-1","payerUuid":"%s","services":[{"cupsCode":"890201","quantity":1,
                 "unitPrice":45000.00,"lineTotal":45000.00,"origin":"TARIFF_MANUAL"}],"packages":[]}"""
                .formatted(UUID.randomUUID()))));
        return new Episode(admission, number);
    }

    private record Episode(UUID uuid, String number) {
    }
}

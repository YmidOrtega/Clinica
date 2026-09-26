package com.ClinicaDeYmid.billing_service;

import com.ClinicaDeYmid.billing_service.application.EpisodeAccountProjection;
import com.ClinicaDeYmid.billing_service.domain.AdmissionKind;
import com.ClinicaDeYmid.billing_service.domain.AdmissionSnapshot;
import com.ClinicaDeYmid.billing_service.support.AdmissionEvents;
import com.ClinicaDeYmid.billing_service.support.BillingSetup;
import com.ClinicaDeYmid.billing_service.support.JwtTestTokens;
import com.ClinicaDeYmid.billing_service.support.StubbedServices;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
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

class InvoiceApiIT extends IntegrationTest {

    private static final String SALES = "/api/v1/billing/sales";
    private static final String INVOICES = "/api/v1/billing/invoices";
    private static final String CONSULTATION = "2c1b0a9f-8e7d-4c6b-9a5f-4e3d2c1b0a9f";
    private static final String PAYER = "7f3a1c2e-9b8d-4e6f-a5b4-c3d2e1f0a9b8";
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));

    @Autowired
    private EpisodeAccountProjection projection;

    @BeforeEach
    void anIssuerWithAnActiveResolution() throws Exception {
        forgetTheBillingSetup();
        as("BILLING", post(BillingSetup.ISSUER), BillingSetup.configuration()).andExpect(status().isCreated());
        String body = as("BILLING", post(BillingSetup.RESOLUTIONS),
                BillingSetup.resolution("18760000001", "SETP", 990000000, 995000000))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        change("BILLING", post(BillingSetup.RESOLUTIONS + "/" + JsonPath.read(body, "$.uuid") + "/activation"), 0, null)
                .andExpect(status().isOk());
    }

    @Test
    void draftsAndIssuesTheInvoiceOfASaleToThePayerWithTheCopayment() throws Exception {
        Episode episode = outpatient("COVERED");
        String sale = confirmedSale(episode);

        String body = as("BILLING", post(INVOICES), drafting(episode, sale))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status.code").value("DRAFT"))
                .andExpect(jsonPath("$.number").doesNotExist())
                .andExpect(jsonPath("$.buyer.kind").value("PAYER"))
                .andExpect(jsonPath("$.buyer.documentNumber").value("900156264-2"))
                .andExpect(jsonPath("$.user.documentNumber").value("1098765432"))
                .andExpect(jsonPath("$.grossTotal").value(45000.00))
                .andExpect(jsonPath("$.patientShare").value(35000.00))
                .andExpect(jsonPath("$.payableTotal").value(10000.00))
                .andExpect(jsonPath("$.lines[0].code").value("890201"))
                .andReturn().getResponse().getContentAsString();
        String invoice = JsonPath.read(body, "$.uuid");

        as("BILLING", post(INVOICES), drafting(episode, sale))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UNIT_ALREADY_INVOICED"));
        change("BILLING", post(SALES + "/" + sale + "/cancellation"), 2, "{\"reason\":\"Error\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SALE_ALREADY_INVOICED"));

        changeWithToken(JwtTestTokens.bearerWithoutSecondFactor("BILLING"), post(INVOICES + "/" + invoice + "/issuance"),
                0, null).andExpect(status().isUnauthorized());
        change("BILLING", post(INVOICES + "/" + invoice + "/issuance"), 0, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("ISSUED"))
                .andExpect(jsonPath("$.number").value("SETP990000000"))
                .andExpect(jsonPath("$.issuedOn").value(TODAY.toString()))
                .andExpect(jsonPath("$.cufe").value(org.hamcrest.Matchers.matchesPattern("^[0-9a-f]{96}$")))
                .andExpect(jsonPath("$.qrContent").value(org.hamcrest.Matchers.containsString("NumFac: SETP990000000")));
        as("BILLING", get(INVOICES + "/" + invoice + "/ubl"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("<cbc:ID>SETP990000000</cbc:ID>")));
        change("BILLING", post(INVOICES + "/" + invoice + "/discard"), 1, "{\"reason\":\"Tarde\"}")
                .andExpect(status().isUnprocessableEntity());

        as("BILLING", get("/api/v1/billing/accounts/" + episode.number() + "/invoices"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].number").value("SETP990000000"));
    }

    @Test
    void aPrivatePatientIsTheBuyerAndPaysEverything() throws Exception {
        Episode episode = outpatient("NOT_COVERED");
        String opened = as("BILLING", post(SALES), "{\"admissionNumber\":\"" + episode.number()
                + "\",\"type\":\"NON_SURGICAL\",\"preloadAuthorized\":false}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String sale = JsonPath.read(opened, "$.uuid");
        String line = JsonPath.read(change("BILLING", post(SALES + "/" + sale + "/lines"), 0,
                "{\"portfolioItemUuid\":\"" + CONSULTATION + "\",\"quantity\":1}").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.lines[0].uuid");
        change("BILLING", post(SALES + "/" + sale + "/lines/" + line + "/manual-price"), 1,
                "{\"unitPrice\":45000,\"reason\":\"Tarifa particular de la clínica\"}").andExpect(status().isOk());
        change("BILLING", post(SALES + "/" + sale + "/confirmation"), 2, null).andExpect(status().isOk());

        as("BILLING", post(INVOICES), drafting(episode, sale))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.buyer.kind").value("PATIENT"))
                .andExpect(jsonPath("$.buyer.name").value("Ana María Restrepo Gómez"))
                .andExpect(jsonPath("$.patientShare").value(0))
                .andExpect(jsonPath("$.payableTotal").value(45000.00));
    }

    @Test
    void anUnresolvedCoverageBlocksTheInvoice() throws Exception {
        Episode episode = outpatient("COVERED");
        String sale = confirmedSale(episode);
        stubEpisode(episode.uuid(), episode.number(), "UNKNOWN");

        as("BILLING", post(INVOICES), drafting(episode, sale))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COVERAGE_PENDING"));
    }

    @Test
    void aDiscardedDraftFreesTheUnitAndAChangedUnitCannotBeIssued() throws Exception {
        Episode episode = outpatient("COVERED");
        String sale = confirmedSale(episode);
        String first = JsonPath.read(as("BILLING", post(INVOICES), drafting(episode, sale))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.uuid");

        change("BILLING", post(INVOICES + "/" + first + "/discard"), 0, "{\"reason\":\"Faltaba revisar el copago\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("DISCARDED"));
        String second = JsonPath.read(as("BILLING", post(INVOICES), drafting(episode, sale))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.uuid");
        as("BILLING", post("/api/v1/billing/accounts/" + episode.number() + "/patient-share-adjustments"),
                "{\"saleUuid\":\"" + sale + "\",\"amount\":4000,\"reason\":\"Cuota moderadora\"}")
                .andExpect(status().isCreated());

        change("BILLING", post(INVOICES + "/" + second + "/issuance"), 0, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVOICE_OUTDATED"));
    }

    @Test
    void anInpatientAccountWaitsForTheDischarge() throws Exception {
        UUID admission = UUID.randomUUID();
        String number = AdmissionEvents.nextNumber();
        projection.follow(new AdmissionSnapshot(admission, number, 1, UUID.randomUUID(), AdmissionKind.INPATIENT,
                AdmissionSnapshot.Status.ACTIVE, UUID.randomUUID(), Instant.parse("2026-09-01T13:00:00Z"), null, null));
        stubEpisode(admission, number, "COVERED");

        as("BILLING", post(INVOICES), "{\"admissionNumber\":\"" + number + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NOT_A_BILLABLE_UNIT"));
    }

    private static String drafting(Episode episode, String sale) {
        return "{\"admissionNumber\":\"" + episode.number() + "\",\"saleUuid\":\"" + sale + "\"}";
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

    private Episode outpatient(String coverage) {
        UUID admission = UUID.randomUUID();
        String number = AdmissionEvents.nextNumber();
        UUID patient = UUID.randomUUID();
        projection.follow(new AdmissionSnapshot(admission, number, 1, patient, AdmissionKind.OUTPATIENT,
                AdmissionSnapshot.Status.ACTIVE, UUID.randomUUID(), Instant.parse("2026-09-01T13:00:00Z"), null, null));
        stubEpisode(admission, number, coverage);
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/portfolio-items/" + CONSULTATION)).willReturn(okJson("""
                {"uuid":"%s","cupsCode":"890201","name":"Consulta","status":{"code":"ACTIVE","offered":true}}"""
                .formatted(CONSULTATION))));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(
                urlPathEqualTo("/api/v1/price-quotes")).willReturn(okJson("""
                {"contractNumber":"CT-1","payerUuid":"%s","services":[{"cupsCode":"890201","quantity":1,
                 "unitPrice":45000.00,"lineTotal":45000.00,"origin":"TARIFF_MANUAL"}],"packages":[]}""".formatted(PAYER))));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/patients/" + patient)).willReturn(okJson("""
                {"uuid":"%s","document":{"type":"CEDULA_DE_CIUDADANIA","number":"1098765432"},
                 "demographics":{"firstNames":"Ana María","lastNames":"Restrepo Gómez","birthDate":"1990-04-12",
                   "sex":"FEMALE"},"affiliation":{"regime":"CONTRIBUTORY"}}""".formatted(patient))));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/payers/" + PAYER)).willReturn(okJson("""
                {"uuid":"%s","socialReason":"Nueva EPS S.A.","nit":"900156264-2","type":"EPS"}""".formatted(PAYER))));
        return new Episode(admission, number);
    }

    private static void stubEpisode(UUID admission, String number, String coverage) {
        String coverageJson = "NOT_COVERED".equals(coverage)
                ? "{\"status\":\"NOT_COVERED\"}"
                : "{\"status\":\"" + coverage + "\",\"contractUuid\":\"" + UUID.randomUUID()
                        + "\",\"contractNumber\":\"CT-1\",\"payerUuid\":\"" + PAYER + "\"}";
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/admissions/episodes/" + admission)).willReturn(okJson("""
                {"uuid":"%s","number":"%s","kind":"OUTPATIENT","status":{"code":"ACTIVE"},"coverage":%s}"""
                .formatted(admission, number, coverageJson))));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/admissions/episodes/" + admission + "/authorizations")).willReturn(okJson("""
                [{"uuid":"%s","number":"AUT-778899","type":"AMBULATORY_SERVICES","authorizedBy":"Nueva EPS",
                  "copayment":35000,"validFrom":"2026-09-01","validTo":"%s","authorizedItems":["%s"],
                  "coversEverything":false,"status":"ACTIVE"}]"""
                .formatted(UUID.randomUUID(), TODAY.plusDays(30), CONSULTATION))));
    }

    private record Episode(UUID uuid, String number) {
    }
}

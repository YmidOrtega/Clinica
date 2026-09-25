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
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SalePricingApiIT extends IntegrationTest {

    private static final String SALES = "/api/v1/billing/sales";
    private static final String QUOTES = "/api/v1/price-quotes";
    private static final String CONTRACT = "4d5e6f70-8192-4a3b-8c4d-5e6f7081920a";
    private static final String PAYER = "7f3a1c2e-9b8d-4e6f-a5b4-c3d2e1f0a9b8";
    private static final String CONSULTATION = "2c1b0a9f-8e7d-4c6b-9a5f-4e3d2c1b0a9f";
    private static final String RARE = "5e4d3c2b-1a0f-4e9d-8c7b-6a5f4e3d2c1b";
    private static final String DELIVERY = "6f5e4d3c-2b1a-4f0e-9d8c-7b6a5f4e3d2c";
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));

    @Autowired
    private EpisodeAccountProjection projection;

    @Test
    void previewsAndThenFreezesThePricesOfTheContract() throws Exception {
        Sale sale = aSaleWith(covered(), CONSULTATION);
        quote(TODAY, List.of("890201"), service("890201", 2, "45000.00", "TARIFF_MANUAL"), "");

        as("BILLING", get(SALES + "/" + sale.uuid() + "/price-preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractNumber").value("CT-2026-001"))
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.lines[0].price.origin").value("TARIFF_MANUAL"))
                .andExpect(jsonPath("$.lines[0].price.lineTotal").value(90000.00))
                .andExpect(jsonPath("$.total").value(90000.00));
        as("BILLING", get(SALES + "/" + sale.uuid()))
                .andExpect(jsonPath("$.settlement").doesNotExist())
                .andExpect(header().string("ETag", "\"1\""));

        change("BILLING", post(SALES + "/" + sale.uuid() + "/confirmation"), 1, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("CONFIRMED"))
                .andExpect(jsonPath("$.lines[0].price.unitPrice").value(45000.00))
                .andExpect(jsonPath("$.lines[0].price.referenceCode").value("ISS2001"))
                .andExpect(jsonPath("$.settlement.contractUuid").value(CONTRACT))
                .andExpect(jsonPath("$.settlement.payerUuid").value(PAYER))
                .andExpect(jsonPath("$.settlement.total").value(90000.00));

        StubbedServices.reset();
        as("BILLING", get(SALES + "/" + sale.uuid()))
                .andExpect(jsonPath("$.settlement.total").value(90000.00));
    }

    @Test
    void chargesAPackageOnceWithItsServicesInZero() throws Exception {
        Sale sale = aSaleWith(covered(), DELIVERY);
        quote(TODAY, List.of("735301"), service("735301", 2, "0.00", "PACKAGE"),
                "{\"uuid\":\"" + UUID.randomUUID() + "\",\"code\":\"PAQ-PARTO\",\"name\":\"Parto vaginal\",\"price\":1800000.00}");

        change("BILLING", post(SALES + "/" + sale.uuid() + "/confirmation"), 1, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].price.origin").value("PACKAGE"))
                .andExpect(jsonPath("$.lines[0].price.billablePerService").value(false))
                .andExpect(jsonPath("$.settlement.packages[0].code").value("PAQ-PARTO"))
                .andExpect(jsonPath("$.settlement.linesTotal").value(0.00))
                .andExpect(jsonPath("$.settlement.total").value(1800000.00));
    }

    @Test
    void twoPackagesDoNotDuplicateTheLines() throws Exception {
        Sale sale = aSaleWith(covered(), DELIVERY);
        quote(TODAY, List.of("735301"), service("735301", 2, "0.00", "PACKAGE"),
                "{\"uuid\":\"" + UUID.randomUUID() + "\",\"code\":\"PAQ-A\",\"name\":\"Paquete A\",\"price\":100.00},"
                        + "{\"uuid\":\"" + UUID.randomUUID() + "\",\"code\":\"PAQ-B\",\"name\":\"Paquete B\",\"price\":200.00}");

        change("BILLING", post(SALES + "/" + sale.uuid() + "/confirmation"), 1, null).andExpect(status().isOk());

        as("BILLING", get(SALES + "/" + sale.uuid()))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.settlement.packages.length()").value(2))
                .andExpect(jsonPath("$.settlement.total").value(300.00));
    }

    @Test
    void aServiceWithoutTariffNeedsAManualPriceWithASecondFactorAndAReason() throws Exception {
        Sale sale = aSaleWith(covered(), RARE);
        quote(TODAY, List.of("999001"), service("999001", 2, "0.00", "UNPRICED"), "");

        change("BILLING", post(SALES + "/" + sale.uuid() + "/confirmation"), 1, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SALE_HAS_UNPRICED_LINES"));

        String manual = "{\"unitPrice\":12500,\"reason\":\"Tarifa institucional acordada con el pagador\"}";
        changeWithToken(JwtTestTokens.bearerWithoutSecondFactor("BILLING"),
                post(SALES + "/" + sale.uuid() + "/lines/" + sale.line() + "/manual-price"), 1, manual)
                .andExpect(status().isUnauthorized());
        changeWithToken(JwtTestTokens.bearerAuthenticatedLongAgo("BILLING"),
                post(SALES + "/" + sale.uuid() + "/lines/" + sale.line() + "/manual-price"), 1, manual)
                .andExpect(status().isUnauthorized());
        change("RECEPTIONIST", post(SALES + "/" + sale.uuid() + "/lines/" + sale.line() + "/manual-price"), 1, manual)
                .andExpect(status().isForbidden());

        change("BILLING", post(SALES + "/" + sale.uuid() + "/lines/" + sale.line() + "/manual-price"), 1, manual)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].manualPrice.unitPrice").value(12500))
                .andExpect(jsonPath("$.lines[0].manualPrice.reason").value("Tarifa institucional acordada con el pagador"));

        change("BILLING", post(SALES + "/" + sale.uuid() + "/confirmation"), 2, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].price.origin").value("MANUAL"))
                .andExpect(jsonPath("$.settlement.total").value(25000.00));
    }

    @Test
    void aServiceTheContractPricesCannotBePricedByHand() throws Exception {
        Sale sale = aSaleWith(covered(), CONSULTATION);
        quote(TODAY, List.of("890201"), service("890201", 2, "45000.00", "TARIFF_MANUAL"), "");

        change("BILLING", post(SALES + "/" + sale.uuid() + "/lines/" + sale.line() + "/manual-price"), 1,
                "{\"unitPrice\":10,\"reason\":\"Descuento\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LINE_PRICED_BY_CONTRACT"));
    }

    @Test
    void aPrivatePatientWithoutPrivateContractIsPricedEntirelyByHand() throws Exception {
        Sale sale = aSaleWith("{\"status\":\"NOT_COVERED\",\"detail\":\"Sin pagador\"}", CONSULTATION);

        as("BILLING", get(SALES + "/" + sale.uuid() + "/price-preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractUuid").doesNotExist())
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.unpricedCups[0]").value("890201"));
        StubbedServices.server().verify(0, postRequestedFor(urlPathEqualTo(QUOTES)));
    }

    @Test
    void refusesToPriceWhileAdmissionsStillChecksTheCoverage() throws Exception {
        Sale sale = aSaleWith("{\"status\":\"UNKNOWN\",\"payerUuid\":\"" + PAYER + "\",\"pending\":true}", CONSULTATION);

        change("BILLING", post(SALES + "/" + sale.uuid() + "/confirmation"), 1, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COVERAGE_PENDING"));
    }

    @Test
    void explainsWhyTheContractCouldNotPriceTheSale() throws Exception {
        Sale sale = aSaleWith(covered(), CONSULTATION);
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo(QUOTES))
                .willReturn(aResponse().withStatus(422).withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"code\":\"CONTRACT_NOT_IN_FORCE\",\"detail\":\"El contrato no está vigente en la fecha\"}")));

        change("BILLING", post(SALES + "/" + sale.uuid() + "/confirmation"), 1, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONTRACT_CANNOT_PRICE"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("no está vigente")));
    }

    @Test
    void withoutContractingNothingIsConfirmed() throws Exception {
        Sale sale = aSaleWith(covered(), CONSULTATION);
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo(QUOTES))
                .willReturn(serverError()));

        change("BILLING", post(SALES + "/" + sale.uuid() + "/confirmation"), 1, null)
                .andExpect(status().isServiceUnavailable());
        as("BILLING", get(SALES + "/" + sale.uuid())).andExpect(jsonPath("$.status.code").value("DRAFT"));
    }

    @Test
    void quotesEachDayAtTheTariffInForceThatDay() throws Exception {
        Sale sale = aSaleWith(covered(), CONSULTATION);
        LocalDate yesterday = TODAY.minusDays(1);
        change("BILLING", post(SALES + "/" + sale.uuid() + "/lines"), 1,
                "{\"portfolioItemUuid\":\"" + CONSULTATION + "\",\"quantity\":1,\"serviceDate\":\"" + yesterday + "\"}")
                .andExpect(status().isOk());
        quote(TODAY, List.of("890201"), service("890201", 2, "45000.00", "TARIFF_MANUAL"), "");
        quote(yesterday, List.of("890201"), service("890201", 1, "40000.00", "TARIFF_MANUAL"), "");

        change("BILLING", post(SALES + "/" + sale.uuid() + "/confirmation"), 2, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement.total").value(130000.00));
    }

    @Test
    void recordsTheManualPriceInTheHistory() throws Exception {
        Sale sale = aSaleWith("{\"status\":\"NOT_COVERED\"}", RARE);

        change("BILLING", post(SALES + "/" + sale.uuid() + "/lines/" + sale.line() + "/manual-price"), 1,
                "{\"unitPrice\":12500,\"reason\":\"Tarifa particular\"}").andExpect(status().isOk());

        assertThat(jdbc.queryForList("""
                SELECT a.manual_price_reason FROM billing_history.sale_lines_aud a WHERE a.uuid = ? ORDER BY a.rev""",
                String.class, sale.line())).containsExactly(null, "Tarifa particular");
    }

    private Sale aSaleWith(String coverage, String portfolioItem) throws Exception {
        UUID admission = UUID.randomUUID();
        String number = AdmissionEvents.nextNumber();
        projection.follow(new AdmissionSnapshot(admission, number, 1, UUID.randomUUID(), AdmissionKind.OUTPATIENT,
                AdmissionSnapshot.Status.ACTIVE, UUID.randomUUID(), Instant.parse("2026-09-01T13:00:00Z"), null, null));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/admissions/episodes/" + admission)).willReturn(okJson("""
                {"uuid":"%s","number":"%s","patientUuid":"%s","kind":"OUTPATIENT","status":{"code":"ACTIVE"},
                 "coverage":%s}""".formatted(admission, number, UUID.randomUUID(), coverage))));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching(
                        "/api/v1/admissions/episodes/[^/]+/authorizations")).willReturn(okJson("[]")));
        String cups = portfolioItem.equals(RARE) ? "999001" : portfolioItem.equals(DELIVERY) ? "735301" : "890201";
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/portfolio-items/" + portfolioItem)).willReturn(okJson("""
                {"uuid":"%s","cupsCode":"%s","name":"Servicio %s","status":{"code":"ACTIVE","offered":true}}"""
                .formatted(portfolioItem, cups, cups))));
        String opened = as("BILLING", post(SALES), "{\"admissionNumber\":\"" + number
                + "\",\"type\":\"NON_SURGICAL\",\"preloadAuthorized\":false}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String uuid = JsonPath.read(opened, "$.uuid");
        String charged = change("BILLING", post(SALES + "/" + uuid + "/lines"), 0,
                "{\"portfolioItemUuid\":\"" + portfolioItem + "\",\"quantity\":2}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Sale(uuid, JsonPath.read(charged, "$.lines[0].uuid"));
    }

    private static String covered() {
        return """
                {"status":"COVERED","contractUuid":"%s","contractNumber":"CT-2026-001","payerUuid":"%s",
                 "pending":false}""".formatted(CONTRACT, PAYER);
    }

    private static String service(String cups, int quantity, String unit, String origin) {
        String total = new java.math.BigDecimal(unit).multiply(java.math.BigDecimal.valueOf(quantity)).toPlainString();
        return """
                {"cupsCode":"%s","quantity":%d,"unitPrice":%s,"lineTotal":%s,"origin":"%s","referenceCode":%s}"""
                .formatted(cups, quantity, unit, total, origin,
                        "TARIFF_MANUAL".equals(origin) ? "\"ISS2001\"" : "null");
    }

    private static void quote(LocalDate on, List<String> cups, String services, String packages) {
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo(QUOTES))
                .withRequestBody(equalToJson("{\"contractUuid\":\"" + CONTRACT + "\",\"on\":\"" + on + "\"}", true, true))
                .willReturn(okJson("""
                        {"contractUuid":"%s","contractNumber":"CT-2026-001","payerUuid":"%s","on":"%s",
                         "services":[%s],"packages":[%s]}""".formatted(CONTRACT, PAYER, on, services, packages))));
    }

    private record Sale(String uuid, String line) {
    }
}

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
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SurgicalSaleApiIT extends IntegrationTest {

    private static final String SALES = "/api/v1/billing/sales";
    private static final String CHOLECYSTECTOMY = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d";
    private static final String HERNIA = "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e";
    private static final String SUTURE_KIT = "3c4d5e6f-7a8b-4c9d-8e1f-2a3b4c5d6e7f";
    private static final String SURGEON = "4d5e6f7a-8b9c-4d0e-9f2a-3b4c5d6e7f8a";
    private static final String ANESTHESIOLOGIST = "5e6f7a8b-9c0d-4e1f-8a3b-4c5d6e7f8a9b";
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));

    @Autowired
    private EpisodeAccountProjection projection;

    @Test
    void liquidatesASurgicalActByComponentWithItsTeam() throws Exception {
        String sale = surgicalSale();
        change("BILLING", post(SALES + "/" + sale + "/procedures"), 0,
                "{\"portfolioItemUuid\":\"" + CHOLECYSTECTOMY + "\",\"route\":\"abdominal\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].kind").value("PROCEDURE"))
                .andExpect(jsonPath("$.lines[0].route").value("ABDOMINAL"))
                .andExpect(jsonPath("$.lines[0].serviceDate").value(TODAY.toString()));
        change("BILLING", post(SALES + "/" + sale + "/procedures"), 1,
                "{\"portfolioItemUuid\":\"" + HERNIA + "\",\"route\":\"abdominal\"}").andExpect(status().isOk());
        change("BILLING", post(SALES + "/" + sale + "/lines"), 2,
                "{\"portfolioItemUuid\":\"" + SUTURE_KIT + "\",\"quantity\":2}").andExpect(status().isOk());
        change("BILLING", put(SALES + "/" + sale + "/surgical-team"), 3, """
                {"members":[{"role":"SURGEON","practitionerUuid":"%s"},
                            {"role":"ANESTHESIOLOGIST","practitionerUuid":"%s"}]}"""
                .formatted(SURGEON, ANESTHESIOLOGIST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.surgery.performedOn").value(TODAY.toString()))
                .andExpect(jsonPath("$.surgery.team[0].fullName").value("Ana María Cirujana"))
                .andExpect(jsonPath("$.surgery.team[0].registrationNumber").value("RM-1001"));

        change("BILLING", post(SALES + "/" + sale + "/confirmation"), 4, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("CONFIRMED"))
                .andExpect(jsonPath("$.lines[0].price.origin").value("SURGICAL_LIQUIDATION"))
                .andExpect(jsonPath("$.lines[0].price.surgical.principal").value(true))
                .andExpect(jsonPath("$.lines[0].price.surgical.components.length()").value(3))
                .andExpect(jsonPath("$.lines[0].price.lineTotal").value(395300.00))
                .andExpect(jsonPath("$.lines[1].price.surgical.sameRoute").value(true))
                .andExpect(jsonPath("$.lines[1].price.lineTotal").value(88850.00))
                .andExpect(jsonPath("$.lines[2].price.origin").value("TARIFF_MANUAL"))
                .andExpect(jsonPath("$.settlement.total").value(395300.00 + 88850.00 + 30000.00));

        assertThat(jdbc.queryForObject("""
                SELECT JSON_LENGTH(l.components) FROM sale_lines l JOIN sales s ON s.id = l.sale_id
                WHERE s.uuid = ? AND l.position = 1""", Integer.class, sale)).isEqualTo(3);

        as("BILLING", get(SALES + "/" + sale + "/practitioner-fees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[?(@.cupsCode == '514201' && @.role == 'SURGEON')].amount").value(900000.00))
                .andExpect(jsonPath("$[?(@.cupsCode == '514201' && @.role == 'SURGEON')].status").value("PAYABLE"))
                .andExpect(jsonPath("$[?(@.cupsCode == '530101' && @.role == 'SURGEON')].status").value("UNAGREED"))
                .andExpect(jsonPath("$[?(@.role == 'ANESTHESIOLOGIST')].status").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("UNAGREED"))));
        as("BILLING", get("/api/v1/billing/practitioner-fees").param("practitionerUuid", SURGEON)
                .param("status", "PAYABLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].saleNumber").isNotEmpty())
                .andExpect(jsonPath("$[0].practitionerName").value("Ana María Cirujana"));

        change("BILLING", post(SALES + "/" + sale + "/cancellation"), 5, "{\"reason\":\"Cirugía reprogramada\"}")
                .andExpect(status().isOk());
        as("BILLING", get(SALES + "/" + sale + "/practitioner-fees"))
                .andExpect(jsonPath("$[*].status").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("VOIDED"))))
                .andExpect(jsonPath("$[0].statusReason").value(org.hamcrest.Matchers.containsString("Cirugía reprogramada")));
    }

    @Test
    void withoutThePractitionersDirectoryTheSurgeryIsNotConfirmed() throws Exception {
        String sale = surgicalSale();
        change("BILLING", post(SALES + "/" + sale + "/procedures"), 0,
                "{\"portfolioItemUuid\":\"" + CHOLECYSTECTOMY + "\",\"route\":\"abdominal\"}").andExpect(status().isOk());
        change("BILLING", post(SALES + "/" + sale + "/procedures"), 1,
                "{\"portfolioItemUuid\":\"" + HERNIA + "\",\"route\":\"abdominal\"}").andExpect(status().isOk());
        change("BILLING", put(SALES + "/" + sale + "/surgical-team"), 2, """
                {"members":[{"role":"SURGEON","practitionerUuid":"%s"},
                            {"role":"ANESTHESIOLOGIST","practitionerUuid":"%s"}]}"""
                .formatted(SURGEON, ANESTHESIOLOGIST)).andExpect(status().isOk());
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching("/api/v1/practitioners/.*/fee-agreements/in-force"))
                .willReturn(serverError()));

        change("BILLING", post(SALES + "/" + sale + "/confirmation"), 3, null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PRACTITIONERS_UNAVAILABLE"));
        as("BILLING", get(SALES + "/" + sale)).andExpect(jsonPath("$.status.code").value("DRAFT"));
    }

    @Test
    void refusesToConfirmWhenTheTeamLacksARoleTheLiquidationPays() throws Exception {
        String sale = surgicalSale();
        change("BILLING", post(SALES + "/" + sale + "/procedures"), 0,
                "{\"portfolioItemUuid\":\"" + CHOLECYSTECTOMY + "\",\"route\":\"abdominal\"}").andExpect(status().isOk());
        change("BILLING", post(SALES + "/" + sale + "/procedures"), 1,
                "{\"portfolioItemUuid\":\"" + HERNIA + "\",\"route\":\"abdominal\"}").andExpect(status().isOk());
        change("BILLING", put(SALES + "/" + sale + "/surgical-team"), 2,
                "{\"members\":[{\"role\":\"SURGEON\",\"practitionerUuid\":\"" + SURGEON + "\"}]}")
                .andExpect(status().isOk());

        change("BILLING", post(SALES + "/" + sale + "/confirmation"), 3, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SURGICAL_TEAM_INCOMPLETE"));
    }

    @Test
    void onlyAttendingPractitionersJoinTheTeam() throws Exception {
        String sale = surgicalSale();
        String retired = UUID.randomUUID().toString();
        practitioner(retired, "Retirado", false);

        change("BILLING", put(SALES + "/" + sale + "/surgical-team"), 0,
                "{\"members\":[{\"role\":\"SURGEON\",\"practitionerUuid\":\"" + retired + "\"}]}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRACTITIONER_NOT_AVAILABLE"));

        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/practitioners/" + SURGEON)).willReturn(serverError()));
        change("BILLING", put(SALES + "/" + sale + "/surgical-team"), 0,
                "{\"members\":[{\"role\":\"SURGEON\",\"practitionerUuid\":\"" + SURGEON + "\"}]}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PRACTITIONERS_UNAVAILABLE"));
    }

    @Test
    void aSurgeryChargedInANonSurgicalSaleIsSentToTheSurgicalOne() throws Exception {
        String sale = saleOf("{\"type\":\"NON_SURGICAL\",\"preloadAuthorized\":false}");
        change("BILLING", post(SALES + "/" + sale + "/lines"), 0,
                "{\"portfolioItemUuid\":\"" + CHOLECYSTECTOMY + "\",\"quantity\":1}").andExpect(status().isOk());
        change("BILLING", post(SALES + "/" + sale + "/procedures"), 1,
                "{\"portfolioItemUuid\":\"" + HERNIA + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PROCEDURE_OUTSIDE_SURGICAL_SALE"));

        as("BILLING", get(SALES + "/" + sale + "/price-preview"))
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.surgeriesOutOfPlace[0]").value("514201"))
                .andExpect(jsonPath("$.unpricedCups.length()").value(0));
        change("BILLING", post(SALES + "/" + sale + "/confirmation"), 1, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SURGERY_NEEDS_SURGICAL_SALE"));
    }

    @Test
    void aSurgicalSaleNeedsTheDayOfTheSurgery() throws Exception {
        UUID admission = UUID.randomUUID();
        String number = episode(admission);

        as("BILLING", post(SALES), "{\"admissionNumber\":\"" + number + "\",\"type\":\"SURGICAL\"}")
                .andExpect(status().isBadRequest());
    }

    private String surgicalSale() throws Exception {
        return saleOf("{\"type\":\"SURGICAL\",\"performedOn\":\"" + TODAY + "\"}");
    }

    private String saleOf(String partial) throws Exception {
        UUID admission = UUID.randomUUID();
        String number = episode(admission);
        UUID contract = UUID.randomUUID();
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/admissions/episodes/" + admission)).willReturn(okJson("""
                {"uuid":"%s","number":"%s","patientUuid":"%s","kind":"INPATIENT","status":{"code":"ACTIVE"},
                 "phases":[{"kind":"INPATIENT","startedAt":"2026-09-01T13:00:00Z","current":true}],
                 "coverage":{"status":"COVERED","contractUuid":"%s","contractNumber":"CT-QX","payerUuid":"%s"}}"""
                .formatted(admission, number, UUID.randomUUID(), contract, UUID.randomUUID()))));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/admissions/episodes/" + admission + "/authorizations"))
                .willReturn(okJson("[]")));
        portfolio(CHOLECYSTECTOMY, "514201", "Colecistectomía");
        portfolio(HERNIA, "530101", "Herniorrafia inguinal");
        portfolio(SUTURE_KIT, "891501", "Kit de sutura");
        practitioner(SURGEON, "Ana María Cirujana", true);
        practitioner(ANESTHESIOLOGIST, "Luis Anestesiólogo", true);
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/practitioners/" + SURGEON + "/fee-agreements/in-force")).willReturn(okJson("""
                {"uuid":"%s","basis":"PER_PROCEDURE","procedures":[{"serviceCode":"514201","amount":900000.00}]}"""
                .formatted(UUID.randomUUID()))));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/practitioners/" + ANESTHESIOLOGIST + "/fee-agreements/in-force"))
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.noContent()));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(
                urlPathEqualTo("/api/v1/price-quotes/surgical")).willReturn(okJson("""
                {"contractUuid":"%1$s","contractNumber":"CT-QX","payerUuid":"%2$s","procedures":[
                  {"cupsCode":"514201","route":"ABDOMINAL","origin":"SURGICAL_LIQUIDATION","surgicalBasis":110,
                   "order":1,"principal":true,"sameRoute":false,"total":395300.00,"referenceCode":"ISS",
                   "components":[{"component":"SURGEON","fullValue":139700,"percent":100,"amount":139700},
                                 {"component":"ANESTHESIOLOGIST","fullValue":105600,"percent":100,"amount":105600},
                                 {"component":"OPERATING_ROOM","fullValue":150000,"percent":100,"amount":150000}]},
                  {"cupsCode":"530101","route":"ABDOMINAL","origin":"SURGICAL_LIQUIDATION","surgicalBasis":60,
                   "order":2,"principal":false,"sameRoute":true,"total":88850.00,"referenceCode":"ISS",
                   "components":[{"component":"SURGEON","fullValue":76200,"percent":50,"amount":38100},
                                 {"component":"ANESTHESIOLOGIST","fullValue":57600,"percent":50,"amount":28800},
                                 {"component":"OPERATING_ROOM","fullValue":43900,"percent":50,"amount":21950}]}],
                 "packages":[]}""".formatted(contract, UUID.randomUUID()))));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(
                urlPathEqualTo("/api/v1/price-quotes")).willReturn(okJson("""
                {"contractNumber":"CT-QX","payerUuid":"%s","services":[
                  {"cupsCode":"891501","quantity":2,"unitPrice":15000.00,"lineTotal":30000.00,
                   "origin":"TARIFF_MANUAL","surgical":false}],"packages":[]}""".formatted(UUID.randomUUID()))));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(
                        urlPathEqualTo("/api/v1/price-quotes"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing("514201"))
                .willReturn(okJson("""
                        {"contractNumber":"CT-QX","payerUuid":"%s","services":[
                          {"cupsCode":"514201","quantity":1,"unitPrice":0,"lineTotal":0,"origin":"UNPRICED",
                           "surgical":true}],"packages":[]}""".formatted(UUID.randomUUID()))));
        String opened = as("BILLING", post(SALES), "{\"admissionNumber\":\"" + number + "\","
                + partial.substring(1)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(opened, "$.uuid");
    }

    private String episode(UUID admission) {
        String number = AdmissionEvents.nextNumber();
        projection.follow(new AdmissionSnapshot(admission, number, 1, UUID.randomUUID(), AdmissionKind.INPATIENT,
                AdmissionSnapshot.Status.ACTIVE, UUID.randomUUID(), Instant.parse("2026-09-01T13:00:00Z"), null, null));
        return number;
    }

    private static void portfolio(String uuid, String cups, String name) {
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/portfolio-items/" + uuid)).willReturn(okJson("""
                {"uuid":"%s","cupsCode":"%s","name":"%s","category":"SURGERY","status":{"code":"ACTIVE","offered":true}}"""
                .formatted(uuid, cups, name))));
    }

    private static void practitioner(String uuid, String name, boolean attends) {
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/practitioners/" + uuid)).willReturn(okJson("""
                {"uuid":"%s","fullName":"%s","registration":{"number":"RM-1001"},
                 "status":{"code":"%s","attends":%s}}""".formatted(uuid, name, attends ? "ACTIVE" : "RETIRED", attends))));
    }
}

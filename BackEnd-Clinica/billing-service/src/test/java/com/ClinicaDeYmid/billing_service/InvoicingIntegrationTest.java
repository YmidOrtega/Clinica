package com.ClinicaDeYmid.billing_service;

import com.ClinicaDeYmid.billing_service.application.DianDelivery;
import com.ClinicaDeYmid.billing_service.application.EpisodeAccountProjection;
import com.ClinicaDeYmid.billing_service.application.clinical.ClinicalFact;
import com.ClinicaDeYmid.billing_service.application.clinical.ClinicalProjection;
import com.ClinicaDeYmid.billing_service.domain.AdmissionKind;
import com.ClinicaDeYmid.billing_service.domain.AdmissionSnapshot;
import com.ClinicaDeYmid.billing_service.support.AdmissionEvents;
import com.ClinicaDeYmid.billing_service.support.BillingSetup;
import com.ClinicaDeYmid.billing_service.support.DianSimulator;
import com.ClinicaDeYmid.billing_service.support.MinistrySimulator;
import com.ClinicaDeYmid.billing_service.support.StubbedServices;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class InvoicingIntegrationTest extends IntegrationTest {

    static final String SALES = "/api/v1/billing/sales";
    static final String INVOICES = "/api/v1/billing/invoices";
    static final String SHARED_PAYMENTS = "/api/v1/billing/shared-payments";
    static final String PRACTITIONER = "0f1e2d3c-4b5a-4968-8778-695a4b3c2d1e";
    static final String CONSULTATION = "2c1b0a9f-8e7d-4c6b-9a5f-4e3d2c1b0a9f";
    static final String PAYER = "7f3a1c2e-9b8d-4e6f-a5b4-c3d2e1f0a9b8";
    static final String CONTRACT = "3c9d2e1f-6a5b-4c7d-8e9f-0a1b2c3d4e5f";
    static final String CUCON = "5f0e2b7c9a1d4e3f8b6a0c2d4e6f8a1b3c5d7e9f0a2b4c6d8e0f1a3b5c7d9e1f";
    static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Bogota"));

    @Autowired
    protected EpisodeAccountProjection projection;

    protected void anActiveResolution(String prefix) throws Exception {
        forgetTheBillingSetup();
        as("BILLING", post(BillingSetup.ISSUER), BillingSetup.configuration()).andExpect(status().isCreated());
        String body = as("BILLING", post(BillingSetup.RESOLUTIONS),
                BillingSetup.resolution("18760000001", prefix, 990000000, 995000000))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        change("BILLING", post(BillingSetup.RESOLUTIONS + "/" + JsonPath.read(body, "$.uuid") + "/activation"), 0, null)
                .andExpect(status().isOk());
    }

    protected String issued() throws Exception {
        Episode episode = outpatient("COVERED");
        return issued(episode, confirmedSale(episode));
    }

    protected String copaymentCollected(Episode episode, String amount) throws Exception {
        return JsonPath.read(as("BILLING", post(SHARED_PAYMENTS), """
                {"admissionNumber":"%s","authorizationNumber":"AUT-778899","amount":%s,"collectionReference":"%s"}"""
                .formatted(episode.number(), amount, UUID.randomUUID())).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.invoiceUuid");
    }

    protected String issued(Episode episode, String sale) throws Exception {
        copaymentCollected(episode, "35000");
        String invoice = JsonPath.read(as("BILLING", post(INVOICES), drafting(episode, sale))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.uuid");
        change("BILLING", post(INVOICES + "/" + invoice + "/issuance"), 0, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signedAt").exists());
        return invoice;
    }

    protected static String drafting(Episode episode, String sale) {
        return "{\"admissionNumber\":\"" + episode.number() + "\",\"saleUuid\":\"" + sale + "\"}";
    }

    protected String confirmedSale(Episode episode) throws Exception {
        String opened = as("BILLING", post(SALES), "{\"admissionNumber\":\"" + episode.number()
                + "\",\"type\":\"NON_SURGICAL\",\"preloadAuthorized\":false}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String sale = JsonPath.read(opened, "$.uuid");
        change("BILLING", post(SALES + "/" + sale + "/lines"), 0,
                "{\"portfolioItemUuid\":\"" + CONSULTATION + "\",\"quantity\":1}").andExpect(status().isOk());
        change("BILLING", post(SALES + "/" + sale + "/confirmation"), 1, null).andExpect(status().isOk());
        return sale;
    }

    protected Episode outpatient(String coverage) {
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
                   "sex":"FEMALE","countryOfOrigin":"CO","countryOfOriginCode":"170"},
                 "affiliation":{"regime":"CONTRIBUTORY","affiliateType":"BENEFICIARY"},
                 "residence":{"department":"Santander","municipality":"Bucaramanga","municipalityCode":"68001",
                   "zone":"URBAN","address":"Calle 45 # 27-10"}}""".formatted(patient))));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/practitioners/" + PRACTITIONER)).willReturn(okJson("""
                {"uuid":"%s","fullName":"Paula Gómez","registration":{"number":"RM-1"},
                 "status":{"code":"ACTIVE","attends":true},"document":{"type":"CEDULA_DE_CIUDADANIA","number":"80100200"}}"""
                .formatted(PRACTITIONER))));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/payers/" + PAYER)).willReturn(okJson("""
                {"uuid":"%s","socialReason":"Nueva EPS S.A.","nit":"900156264-2","type":"EPS"}""".formatted(PAYER))));
        return new Episode(admission, number);
    }

    protected static void stubEpisode(UUID admission, String number, String coverage) {
        String coverageJson = "NOT_COVERED".equals(coverage)
                ? "{\"status\":\"NOT_COVERED\"}"
                : "{\"status\":\"" + coverage + "\",\"contractUuid\":\"" + CONTRACT
                        + "\",\"contractNumber\":\"CT-1\",\"payerUuid\":\"" + PAYER + "\"}";
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/contracts/" + CONTRACT)).willReturn(okJson("""
                {"uuid":"%s","number":"CT-1","modality":"EVENT","coveragePlan":"UPC_CONTRIBUTORY",
                 "coveragePlanCode":"16","cucon":"%s"}""".formatted(CONTRACT, CUCON))));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/admissions/episodes/" + admission)).willReturn(okJson("""
                {"uuid":"%s","number":"%s","kind":"OUTPATIENT","status":{"code":"ACTIVE"},"coverage":%s,
                 "attending":{"practitionerUuid":"%s","fullName":"Paula Gómez","registrationNumber":"RM-1"},
                 "phases":[{"kind":"OUTPATIENT","startedAt":"2026-09-01T13:00:00Z","endedAt":null}]}"""
                .formatted(admission, number, coverageJson, PRACTITIONER))));
        StubbedServices.server().stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/admissions/episodes/" + admission + "/authorizations")).willReturn(okJson("""
                [{"uuid":"%s","number":"AUT-778899","type":"AMBULATORY_SERVICES","authorizedBy":"Nueva EPS",
                  "copayment":35000,"validFrom":"2026-09-01","validTo":"%s","authorizedItems":["%s"],
                  "coversEverything":false,"status":"ACTIVE"}]"""
                .formatted(UUID.randomUUID(), TODAY.plusDays(30), CONSULTATION))));
    }

    protected record Episode(UUID uuid, String number) {
    }

    protected String acceptedInvoice(DianDelivery delivery) throws Exception {
        Episode episode = outpatient("COVERED");
        return acceptedInvoice(delivery, episode, confirmedSale(episode));
    }

    protected String acceptedInvoice(DianDelivery delivery, Episode episode, String sale) throws Exception {
        String invoice = issued(episode, sale);
        DianSimulator.doesNotKnowTheDocument();
        DianSimulator.receivesTheTestSet("zip-" + UUID.randomUUID());
        delivery.deliverPending(100);
        DianSimulator.validates("GetStatusZip");
        delivery.checkPending(100);
        as("BILLING", get(INVOICES + "/" + invoice)).andExpect(jsonPath("$.dian.status").value("ACCEPTED"));
        return invoice;
    }

    protected static void documentedCare(ClinicalProjection clinical, Episode episode) {
        ZoneId bogota = ZoneId.of("America/Bogota");
        UUID encounter = UUID.randomUUID();
        Instant opened = LocalDate.now(bogota).atTime(9, 30).atZone(bogota).toInstant();
        clinical.follow(new ClinicalFact.EncounterOpened(encounter, episode.uuid(), UUID.randomUUID(), "OUTPATIENT",
                opened, new ClinicalFact.CareSetting("328", "01", "01")));
        clinical.follow(new ClinicalFact.NoteSigned(UUID.randomUUID(), encounter, episode.uuid(), "CONSULTATION",
                opened.plusSeconds(600), "15", "38",
                List.of(new ClinicalFact.CodedDiagnosis("I10X", "PRINCIPAL", "CONFIRMED_NEW"))));
    }

    protected String numberOf(String invoice) throws Exception {
        return JsonPath.read(as("BILLING", get(INVOICES + "/" + invoice)).andReturn().getResponse()
                .getContentAsString(), "$.number");
    }

    protected String validatedInvoice(String prefix, DianDelivery delivery, ClinicalProjection clinical)
            throws Exception {
        anActiveResolution(prefix);
        Episode episode = outpatient("COVERED");
        String sale = confirmedSale(episode);
        documentedCare(clinical, episode);
        String invoice = acceptedInvoice(delivery, episode, sale);
        MinistrySimulator.logsIn();
        MinistrySimulator.validates(numberOf(invoice));
        as("BILLING", post(INVOICES + "/" + invoice + "/rips-validation"))
                .andExpect(jsonPath("$.status").value("VALIDATED"));
        return invoice;
    }

    protected String filedInvoice(String prefix, DianDelivery delivery, ClinicalProjection clinical) throws Exception {
        String invoice = validatedInvoice(prefix, delivery, clinical);
        as("BILLING", post(INVOICES + "/" + invoice + "/filing"),
                "{\"filingNumber\":\"RAD-" + prefix + "\",\"filedOn\":\"" + TODAY + "\"}")
                .andExpect(status().isCreated());
        return invoice;
    }
}

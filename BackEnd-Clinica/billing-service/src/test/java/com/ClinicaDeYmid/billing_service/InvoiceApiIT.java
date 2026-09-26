package com.ClinicaDeYmid.billing_service;

import com.ClinicaDeYmid.billing_service.application.DianDelivery;
import com.ClinicaDeYmid.billing_service.application.DocumentSigning;
import com.ClinicaDeYmid.billing_service.domain.AdmissionKind;
import com.ClinicaDeYmid.billing_service.domain.AdmissionSnapshot;
import com.ClinicaDeYmid.billing_service.support.AdmissionEvents;
import com.ClinicaDeYmid.billing_service.support.DianSimulator;
import com.ClinicaDeYmid.billing_service.support.JwtTestTokens;
import com.ClinicaDeYmid.billing_service.support.LocalDianSigningKey;
import com.ClinicaDeYmid.billing_service.support.XadesVerification;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvoiceApiIT extends InvoicingIntegrationTest {

    @Autowired
    private DocumentSigning signing;

    @Autowired
    private DianDelivery delivery;

    @BeforeEach
    void anIssuerWithAnActiveResolution() throws Exception {
        anActiveResolution("SETP");
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
                .andExpect(jsonPath("$.qrContent").value(org.hamcrest.Matchers.containsString("NumFac: SETP990000000")))
                .andExpect(jsonPath("$.signedAt").exists());
        String ubl = as("BILLING", get(INVOICES + "/" + invoice + "/ubl"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("<cbc:ID>SETP990000000</cbc:ID>")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(XadesVerification.verify(ubl, LocalDianSigningKey.SHARED.certificate().getPublicKey()).valid())
                .isTrue();
        change("BILLING", post(INVOICES + "/" + invoice + "/discard"), 1, "{\"reason\":\"Tarde\"}")
                .andExpect(status().isUnprocessableEntity());

        as("BILLING", get("/api/v1/billing/accounts/" + episode.number() + "/invoices"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].number").value("SETP990000000"));
    }

    @Test
    void anIssuedInvoiceStaysUnsignedWhileTheKeyIsUnreachableAndIsSignedOnRetry() throws Exception {
        anActiveResolution("SETS");
        String first = issuedWhileTheKeyIsUnreachable();
        String second = issuedWhileTheKeyIsUnreachable();

        as("BILLING", get(INVOICES + "/" + first + "/ubl"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ds:Signature"))));
        as("BILLING", post(INVOICES + "/" + first + "/signature"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DIAN_SIGNATURE_UNAVAILABLE"));

        LocalDianSigningKey.SHARED.available(true);
        String signedAt = JsonPath.read(as("BILLING", post(INVOICES + "/" + first + "/signature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signedAt").exists())
                .andReturn().getResponse().getContentAsString(), "$.signedAt");
        as("BILLING", post(INVOICES + "/" + first + "/signature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signedAt").value(signedAt));
        assertThat(signing.signPending(50)).isGreaterThanOrEqualTo(1);
        as("BILLING", get(INVOICES + "/" + second))
                .andExpect(jsonPath("$.signedAt").exists());
        String ubl = as("BILLING", get(INVOICES + "/" + second + "/ubl")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(XadesVerification.verify(ubl, LocalDianSigningKey.SHARED.certificate().getPublicKey()).valid())
                .isTrue();
    }

    @Test
    void aSignedInvoiceTravelsToTheDianTestSetUntilItIsAccepted() throws Exception {
        anActiveResolution("SETD");
        String invoice = issued();
        DianSimulator.receivesTheTestSet("zip-accepted");
        DianSimulator.doesNotKnowTheDocument();

        delivery.deliverPending(50);

        as("BILLING", get(INVOICES + "/" + invoice))
                .andExpect(jsonPath("$.dian.status").value("AWAITING_VALIDATION"))
                .andExpect(jsonPath("$.dian.trackId").value("zip-accepted"))
                .andExpect(jsonPath("$.dian.fileName").value(org.hamcrest.Matchers.matchesPattern(
                        "^z0800197268000[0-9]{2}[0-9a-f]{8}\\.zip$")))
                .andExpect(jsonPath("$.dian.attempts").value(1));
        DianSimulator.isStillProcessing();
        delivery.checkPending(50);
        as("BILLING", get(INVOICES + "/" + invoice)).andExpect(jsonPath("$.dian.status").value("AWAITING_VALIDATION"));
        DianSimulator.validates("GetStatusZip");
        delivery.checkPending(50);

        as("BILLING", get(INVOICES + "/" + invoice)).andExpect(jsonPath("$.dian.status").value("ACCEPTED"));
        as("BILLING", get(INVOICES + "/" + invoice + "/dian-verdicts"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].operation").value("SEND_TEST_SET"))
                .andExpect(jsonPath("$[0].outcome").value("RECEIVED"))
                .andExpect(jsonPath("$[1].outcome").value("PROCESSING"))
                .andExpect(jsonPath("$[2].outcome").value("ACCEPTED"))
                .andExpect(jsonPath("$[2].statusCode").value("00"));
        assertThat(jdbc.queryForObject("""
                SELECT f.content FROM document_files f
                JOIN electronic_documents e ON e.id = f.electronic_document_id
                JOIN invoices i ON i.id = e.invoice_id
                WHERE i.uuid = ? AND f.kind = 'DIAN_APPLICATION_RESPONSE'""", String.class, invoice))
                .isEqualTo(DianSimulator.APPLICATION_RESPONSE);
        as("BILLING", post(INVOICES + "/" + invoice + "/dian-delivery"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_DELIVERABLE"));
    }

    @Test
    void aRejectedInvoiceWaitsForAManualResendThatKeepsItsNumber() throws Exception {
        anActiveResolution("SETR");
        String invoice = issued();
        DianSimulator.receivesTheTestSet("zip-first");
        String number = JsonPath.read(as("BILLING", post(INVOICES + "/" + invoice + "/dian-delivery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dian.status").value("AWAITING_VALIDATION"))
                .andReturn().getResponse().getContentAsString(), "$.number");
        DianSimulator.rejects("GetStatusZip", "Regla: FAD06, Rechazo: el CUFE no corresponde");
        delivery.checkPending(50);

        String rejected = as("BILLING", get(INVOICES + "/" + invoice))
                .andExpect(jsonPath("$.dian.status").value("REJECTED"))
                .andReturn().getResponse().getContentAsString();
        delivery.deliverPending(50);
        as("BILLING", get(INVOICES + "/" + invoice + "/dian-verdicts"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].errors[0]").value("Regla: FAD06, Rechazo: el CUFE no corresponde"));

        DianSimulator.doesNotKnowTheDocument();
        DianSimulator.receivesTheTestSet("zip-second");
        as("BILLING", post(INVOICES + "/" + invoice + "/dian-delivery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(number))
                .andExpect(jsonPath("$.dian.status").value("AWAITING_VALIDATION"))
                .andExpect(jsonPath("$.dian.trackId").value("zip-second"))
                .andExpect(jsonPath("$.dian.attempts").value(2))
                .andExpect(jsonPath("$.dian.fileName").value(org.hamcrest.Matchers.not(
                        (String) JsonPath.read(rejected, "$.dian.fileName"))));
    }

    @Test
    void anUnreachableDianLeavesTheInvoiceQueued() throws Exception {
        anActiveResolution("SETU");
        String invoice = issued();
        DianSimulator.isDown();

        as("BILLING", post(INVOICES + "/" + invoice + "/dian-delivery"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DIAN_UNAVAILABLE"));
        as("BILLING", get(INVOICES + "/" + invoice))
                .andExpect(jsonPath("$.dian.status").doesNotExist())
                .andExpect(jsonPath("$.dian.attempts").value(1));
        as("BILLING", get(INVOICES + "/" + invoice + "/dian-verdicts")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void onlyAnIssuedInvoiceIsSigned() throws Exception {
        Episode episode = outpatient("COVERED");
        String sale = confirmedSale(episode);
        String draft = JsonPath.read(as("BILLING", post(INVOICES), drafting(episode, sale))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.uuid");

        as("BILLING", post(INVOICES + "/" + draft + "/signature"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVOICE_NOT_ISSUED"));
        as("RECEPTIONIST", post(INVOICES + "/" + draft + "/signature")).andExpect(status().isForbidden());
    }

    private String issuedWhileTheKeyIsUnreachable() throws Exception {
        Episode episode = outpatient("COVERED");
        String sale = confirmedSale(episode);
        String invoice = JsonPath.read(as("BILLING", post(INVOICES), drafting(episode, sale))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.uuid");
        LocalDianSigningKey.SHARED.available(false);
        change("BILLING", post(INVOICES + "/" + invoice + "/issuance"), 0, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("ISSUED"))
                .andExpect(jsonPath("$.signedAt").doesNotExist());
        return invoice;
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

}

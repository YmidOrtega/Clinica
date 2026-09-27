package com.ClinicaDeYmid.billing_service;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SharedPaymentApiIT extends InvoicingIntegrationTest {

    @Test
    void aRetriedCollectionReturnsTheSameInvoiceAndAReusedReferenceIsRefused() throws Exception {
        anActiveResolution("SPA");
        Episode episode = outpatient("COVERED");
        String reference = UUID.randomUUID().toString();
        String request = collection(episode, "AUT-778899", "35000", reference, "COPAYMENT");

        String first = as("BILLING", post(SHARED_PAYMENTS), request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("COPAYMENT"))
                .andExpect(jsonPath("$.number").value(org.hamcrest.Matchers.startsWith("SPA")))
                .andExpect(jsonPath("$.signedAt").exists())
                .andReturn().getResponse().getContentAsString();
        as("BILLING", post(SHARED_PAYMENTS), request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value((String) JsonPath.read(first, "$.number")));
        as("BILLING", post(SHARED_PAYMENTS), collection(episode, "AUT-778899", "30000", reference, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COLLECTION_REFERENCE_REUSED"));
    }

    @Test
    void refusesPaymentsItCannotTieToTheEpisodeOrTheCashier() throws Exception {
        anActiveResolution("SPB");
        Episode covered = outpatient("COVERED");
        Episode privately = outpatient("NOT_COVERED");

        as("BILLING", post(SHARED_PAYMENTS), collection(covered, "AUT-000000", "35000", UUID.randomUUID().toString(), null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SHARED_PAYMENT_NOT_ACCEPTED"));
        as("BILLING", post(SHARED_PAYMENTS), collection(privately, null, "35000", UUID.randomUUID().toString(), null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SHARED_PAYMENT_NOT_ACCEPTED"));
        as("RECEPTIONIST", post(SHARED_PAYMENTS), collection(covered, null, "35000", UUID.randomUUID().toString(), null))
                .andExpect(status().isForbidden());
        as("BILLING", post(SHARED_PAYMENTS), collection(covered, null, "0", UUID.randomUUID().toString(), null))
                .andExpect(status().isBadRequest());
        as("BILLING", post(SHARED_PAYMENTS),
                collection(covered, null, "35000", UUID.randomUUID().toString(), "RECOVERY_FEE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void showsWhatIsExpectedInvoicedAndPendingAndBlocksInvoicingThePayerForMore() throws Exception {
        anActiveResolution("SPC");
        Episode episode = outpatient("COVERED");
        String sale = confirmedSale(episode);

        as("BILLING", get("/api/v1/billing/accounts/" + episode.number() + "/shared-payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposedKind").value("MODERATING_FEE"))
                .andExpect(jsonPath("$.units[0].expected").value(35000.00))
                .andExpect(jsonPath("$.units[0].authorizations[0]").value("AUT-778899"))
                .andExpect(jsonPath("$.units[0].pending").value(35000.00));
        copaymentCollected(episode, "30000");
        copaymentCollected(episode, "10000");
        as("BILLING", get("/api/v1/billing/accounts/" + episode.number() + "/shared-payments"))
                .andExpect(jsonPath("$.units[0].invoiced.length()").value(2))
                .andExpect(jsonPath("$.units[0].pending").value(-5000.00));

        as("BILLING", post(INVOICES), drafting(episode, sale))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SHARED_PAYMENT_EXCEEDS_EXPECTED"));
    }

    @Test
    void thePayerInvoiceCreditsWhatWasCollectedGroupedByConcept() throws Exception {
        anActiveResolution("SPD");
        Episode episode = outpatient("COVERED");
        String sale = confirmedSale(episode);
        as("BILLING", post(SHARED_PAYMENTS), collection(episode, "AUT-778899", "20000", UUID.randomUUID().toString(),
                "COPAYMENT")).andExpect(status().isCreated());
        as("BILLING", post(SHARED_PAYMENTS), collection(episode, "AUT-778899", "15000", UUID.randomUUID().toString(),
                "COPAYMENT")).andExpect(status().isCreated());
        String invoice = JsonPath.read(as("BILLING", post(INVOICES), drafting(episode, sale))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.uuid");
        change("BILLING", post(INVOICES + "/" + invoice + "/issuance"), 0, null).andExpect(status().isOk());

        String ubl = as("BILLING", get(INVOICES + "/" + invoice + "/ubl")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(ubl).contains("<cbc:CustomizationID>SS-CUFE",
                "<cac:PrepaidPayment><cbc:ID schemeID=\"01\">1</cbc:ID><cbc:PaidAmount currencyID=\"COP\">35000.00",
                "<cbc:PrepaidAmount currencyID=\"COP\">35000.00",
                "<Name>MODALIDAD_PAGO</Name><Value schemeID=\"04\" schemeName=\"salud_modalidad_pago.gc\">Pago por evento",
                "<Name>NUMERO_CONTRATO</Name><Value>" + CUCON + "</Value>")
                .doesNotContain("<cbc:ID schemeID=\"01\">2</cbc:ID>", "<Name>COPAGO</Name>");
    }

    private static String collection(Episode episode, String authorization, String amount, String reference,
                                     String kind) {
        return """
                {"admissionNumber":"%s",%s"amount":%s,"collectionReference":"%s"%s}""".formatted(episode.number(),
                authorization == null ? "" : "\"authorizationNumber\":\"" + authorization + "\",", amount, reference,
                kind == null ? "" : ",\"kind\":\"" + kind + "\"");
    }
}

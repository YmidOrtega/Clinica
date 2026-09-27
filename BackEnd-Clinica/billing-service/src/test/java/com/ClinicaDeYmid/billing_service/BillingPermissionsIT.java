package com.ClinicaDeYmid.billing_service;

import com.ClinicaDeYmid.billing_service.application.DianDelivery;
import com.ClinicaDeYmid.billing_service.application.clinical.ClinicalProjection;
import com.ClinicaDeYmid.billing_service.support.BillingSetup;
import com.ClinicaDeYmid.billing_service.support.JwtTestTokens;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BillingPermissionsIT extends InvoicingIntegrationTest {

    private static final String OBJECTIONS = "/api/v1/billing/objections";

    @Autowired
    private DianDelivery delivery;

    @Autowired
    private ClinicalProjection clinical;

    @Test
    void onlyTheAdministrationConfiguresBillingAndAlwaysWithARecentSecondFactor() throws Exception {
        forgetTheBillingSetup();
        as("BILLING", post(BillingSetup.ISSUER), BillingSetup.configuration()).andExpect(status().isForbidden());
        changeWithToken(JwtTestTokens.bearerWithoutSecondFactor("ADMIN"), post(BillingSetup.ISSUER), 0,
                BillingSetup.configuration()).andExpect(status().isUnauthorized());
        as("ADMIN", post(BillingSetup.ISSUER), BillingSetup.configuration()).andExpect(status().isCreated());
        String resolution = JsonPath.read(as("ADMIN", post(BillingSetup.RESOLUTIONS),
                        BillingSetup.resolution("18760000001", "PERA", 990000000, 995000000))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.uuid");
        changeWithToken(JwtTestTokens.bearerWithoutSecondFactor("ADMIN"),
                post(BillingSetup.RESOLUTIONS + "/" + resolution + "/activation"), 0, null)
                .andExpect(status().isUnauthorized());
        change("BILLING", post(BillingSetup.RESOLUTIONS + "/" + resolution + "/activation"), 0, null)
                .andExpect(status().isForbidden());
        changeWithToken(JwtTestTokens.bearerWithoutSecondFactor("ADMIN"), post(BillingSetup.ISSUER + "/production"), 0,
                null).andExpect(status().isUnauthorized());
        change("BILLING", put("/api/v1/billing/stay-charges/PRIVATE_ROOM"), 0,
                "{\"portfolioItemUuid\":\"" + java.util.UUID.randomUUID() + "\"}")
                .andExpect(status().isForbidden());
        as("BILLING", get(BillingSetup.ISSUER)).andExpect(status().isOk());
    }

    @Test
    void accountsReceivableFilesAndAnswersGlossesButNeverInvoicesNorAcceptsValue() throws Exception {
        anActiveResolution("PERB");
        Episode episode = outpatient("COVERED");
        String sale = confirmedSale(episode);
        documentedCare(clinical, episode);
        String invoice = acceptedInvoice(delivery, episode, sale);
        com.ClinicaDeYmid.billing_service.support.MinistrySimulator.logsIn();
        com.ClinicaDeYmid.billing_service.support.MinistrySimulator.validates(numberOf(invoice));

        as("ACCOUNTS_RECEIVABLE", post(SALES), "{\"admissionNumber\":\"" + episode.number()
                + "\",\"type\":\"NON_SURGICAL\",\"preloadAuthorized\":false}").andExpect(status().isForbidden());
        as("ACCOUNTS_RECEIVABLE", post(INVOICES), drafting(episode, sale)).andExpect(status().isForbidden());
        change("ACCOUNTS_RECEIVABLE", post(INVOICES + "/" + invoice + "/credit-notes"), 1,
                "{\"concept\":\"VOID\",\"reason\":\"x\"}").andExpect(status().isForbidden());
        as("BILLING", post(INVOICES + "/" + invoice + "/rips-validation")).andExpect(status().isOk());
        as("ACCOUNTS_RECEIVABLE", get(INVOICES + "/" + invoice + "/rips-validations")).andExpect(status().isOk());
        as("ACCOUNTS_RECEIVABLE", post(INVOICES + "/" + invoice + "/filing"),
                "{\"filingNumber\":\"RAD-PERB\",\"filedOn\":\"" + TODAY + "\"}").andExpect(status().isCreated());
        changeWithToken(JwtTestTokens.bearerWithoutSecondFactor("ACCOUNTS_RECEIVABLE"),
                put(INVOICES + "/" + invoice + "/filing"), 0,
                "{\"filingNumber\":\"RAD-PERB-2\",\"filedOn\":\"" + TODAY + "\",\"reason\":\"Digitación\"}")
                .andExpect(status().isUnauthorized());
        change("ACCOUNTS_RECEIVABLE", put(INVOICES + "/" + invoice + "/filing"), 0,
                "{\"filingNumber\":\"RAD-PERB-2\",\"filedOn\":\"" + TODAY + "\",\"reason\":\"Digitación\"}")
                .andExpect(status().isOk());

        String gloss = JsonPath.read(as("ACCOUNTS_RECEIVABLE", post(INVOICES + "/" + invoice + "/objections"), """
                        {"kind":"GLOSS","payerRecord":"GL-PER","notifiedOn":"%s",
                         "items":[{"invoiceLinePosition":1,"code":"SO3401","amount":2000}]}""".formatted(TODAY))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.uuid");
        change("ACCOUNTS_RECEIVABLE", post(OBJECTIONS + "/" + gloss + "/response"), 0, answer("RE9702", 2000))
                .andExpect(status().isForbidden());
        change("ACCOUNTS_RECEIVABLE", post(OBJECTIONS + "/" + gloss + "/response"), 0, answer("RE9602", 0))
                .andExpect(status().isOk());
        changeWithToken(JwtTestTokens.bearerWithoutSecondFactor("ACCOUNTS_RECEIVABLE"),
                post(OBJECTIONS + "/" + gloss + "/decision"), 1,
                "{\"decidedOn\":\"" + TODAY + "\",\"rulings\":[{\"position\":1,\"upheldAmount\":0}]}")
                .andExpect(status().isUnauthorized());
        change("ACCOUNTS_RECEIVABLE", post(OBJECTIONS + "/" + gloss + "/decision"), 1,
                "{\"decidedOn\":\"" + TODAY + "\",\"rulings\":[{\"position\":1,\"upheldAmount\":0}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("LIFTED"));
        as("RECEPTIONIST", get(OBJECTIONS + "/pending")).andExpect(status().isForbidden());
    }

    private static String answer(String code, int accepted) {
        return "{\"responseRecord\":\"RP-PER\",\"respondedOn\":\"" + TODAY + "\",\"answers\":[{\"position\":1,"
                + "\"code\":\"" + code + "\",\"acceptedAmount\":" + accepted + "}]}";
    }
}

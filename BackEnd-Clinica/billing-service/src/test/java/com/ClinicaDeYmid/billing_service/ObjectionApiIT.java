package com.ClinicaDeYmid.billing_service;

import com.ClinicaDeYmid.billing_service.application.DianDelivery;
import com.ClinicaDeYmid.billing_service.application.clinical.ClinicalProjection;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ObjectionApiIT extends InvoicingIntegrationTest {

    private static final String OBJECTIONS = "/api/v1/billing/objections";

    @Autowired
    private DianDelivery delivery;

    @Autowired
    private ClinicalProjection clinical;

    @Test
    void aPartlyAcceptedGlossIssuesItsCreditNoteAndThePayerDecidesTheRest() throws Exception {
        String invoice = filedInvoice("GLOA", delivery, clinical);

        String objection = JsonPath.read(as("BILLING", post(objectionsOf(invoice)), gloss("GL-778", TODAY,
                        "{\"invoiceLinePosition\":1,\"code\":\"TA0201\",\"amount\":8000,"
                                + "\"detail\":\"La consulta supera la tarifa pactada\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("GLOSS"))
                .andExpect(jsonPath("$.status").value("AWAITING_RESPONSE"))
                .andExpect(jsonPath("$.claimedAmount").value(8000))
                .andExpect(jsonPath("$.extemporaneous").value(false))
                .andExpect(jsonPath("$.state").value("ON_TIME"))
                .andExpect(jsonPath("$.remainingBusinessDays").value(15))
                .andExpect(jsonPath("$.items[0].code").value("TA0201"))
                .andReturn().getResponse().getContentAsString(), "$.uuid");
        as("BILLING", get(OBJECTIONS + "/pending"))
                .andExpect(jsonPath("$[?(@.uuid == '" + objection + "')].state").value(
                        org.hamcrest.Matchers.contains("ON_TIME")));

        as("BILLING", post(OBJECTIONS + "/" + objection + "/response"),
                response("RE9801", 3000)).andExpect(status().isPreconditionRequired());
        String etag = change("BILLING", post(OBJECTIONS + "/" + objection + "/response"), 0, response("RE9801", 3000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESPONDED"))
                .andExpect(jsonPath("$.acceptedAmount").value(3000))
                .andExpect(jsonPath("$.respondedLate").value(false))
                .andExpect(jsonPath("$.creditNoteNumber").exists())
                .andExpect(jsonPath("$.items[0].responseCode").value("RE9801"))
                .andExpect(jsonPath("$.decisionDeadline").exists())
                .andReturn().getResponse().getHeader("ETag");
        long version = Long.parseLong(etag.replace("\"", ""));
        as("BILLING", get(INVOICES + "/" + invoice)).andExpect(jsonPath("$.creditedTotal").value(3000));
        as("BILLING", get(OBJECTIONS + "/pending"))
                .andExpect(jsonPath("$[?(@.uuid == '" + objection + "')]").isEmpty());

        change("BILLING", post(OBJECTIONS + "/" + objection + "/decision"), version, decision(6000))
                .andExpect(status().isBadRequest());
        change("BILLING", post(OBJECTIONS + "/" + objection + "/decision"), version, decision(2000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECIDED"))
                .andExpect(jsonPath("$.upheldAmount").value(2000))
                .andExpect(jsonPath("$.outcome").value("PARTIALLY_LIFTED"))
                .andExpect(jsonPath("$.items[0].upheldAmount").value(2000));
    }

    @Test
    void anAgreementFollowUpGlossNamesNoLineAndItsAcceptedValueIsCreditedAcrossTheInvoice() throws Exception {
        String invoice = filedInvoice("GLOS", delivery, clinical);

        as("BILLING", post(objectionsOf(invoice)), gloss("GL-SA0", TODAY, "{\"code\":\"TA0201\",\"amount\":2500}"))
                .andExpect(status().isBadRequest());
        String objection = JsonPath.read(as("BILLING", post(objectionsOf(invoice)), gloss("GL-SA1", TODAY,
                        "{\"code\":\"SA5601\",\"amount\":2500,\"detail\":\"Metas de calidad incumplidas\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].invoiceLinePosition").doesNotExist())
                .andReturn().getResponse().getContentAsString(), "$.uuid");

        change("BILLING", post(OBJECTIONS + "/" + objection + "/response"), 0, response("RE9801", 2000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedAmount").value(2000))
                .andExpect(jsonPath("$.creditNoteNumber").exists());
        as("BILLING", get(INVOICES + "/" + invoice)).andExpect(jsonPath("$.creditedTotal").value(2000));
    }

    @Test
    void anAcceptedDevolutionVoidsTheInvoiceAndHappensOnlyOnce() throws Exception {
        String invoice = filedInvoice("GLOB", delivery, clinical);

        String objection = JsonPath.read(as("BILLING", post(objectionsOf(invoice)), """
                        {"kind":"DEVOLUTION","payerRecord":"DV-1","notifiedOn":"%s",
                         "items":[{"code":"DE1601","detail":"El afiliado pertenecía a otra EPS"}]}"""
                        .formatted(TODAY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimedAmount").value(10000))
                .andExpect(jsonPath("$.remainingBusinessDays").value(5))
                .andReturn().getResponse().getContentAsString(), "$.uuid");
        as("BILLING", post(objectionsOf(invoice)), """
                {"kind":"DEVOLUTION","payerRecord":"DV-2","notifiedOn":"%s","items":[{"code":"DE4401"}]}"""
                .formatted(TODAY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_OBJECTION"));

        change("BILLING", post(OBJECTIONS + "/" + objection + "/response"), 0, response("RE9701", 10000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedAmount").value(10000))
                .andExpect(jsonPath("$.creditNoteNumber").exists());
        as("BILLING", get(INVOICES + "/" + invoice)).andExpect(jsonPath("$.status.code").value("VOIDED"));
    }

    @Test
    void refusesWhatTheManualDoesNotAllow() throws Exception {
        anActiveResolution("GLOC");
        String notFiled = issued();
        as("BILLING", post(objectionsOf(notFiled)), gloss("GL-1", TODAY,
                        "{\"invoiceLinePosition\":1,\"code\":\"TA0201\",\"amount\":1000}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVOICE_NOT_FILED"));

        String invoice = filedInvoice("GLOD", delivery, clinical);
        for (String item : new String[]{
                "{\"invoiceLinePosition\":1,\"code\":\"DE1601\",\"amount\":1000}",
                "{\"invoiceLinePosition\":1,\"code\":\"TA02\",\"amount\":1000}",
                "{\"invoiceLinePosition\":1,\"code\":\"ZZ0101\",\"amount\":1000}",
                "{\"invoiceLinePosition\":7,\"code\":\"TA0201\",\"amount\":1000}",
                "{\"invoiceLinePosition\":1,\"code\":\"TA0201\",\"amount\":20000}"}) {
            as("BILLING", post(objectionsOf(invoice)), gloss("GL-2", TODAY, item))
                    .andExpect(status().isBadRequest());
        }
        as("BILLING", post(objectionsOf(invoice)), gloss("GL-3", TODAY.plusDays(1),
                        "{\"invoiceLinePosition\":1,\"code\":\"TA0201\",\"amount\":1000}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_OBJECTION"));
        as("RECEPTIONIST", post(objectionsOf(invoice)), gloss("GL-4", TODAY,
                "{\"invoiceLinePosition\":1,\"code\":\"TA0201\",\"amount\":1000}"))
                .andExpect(status().isForbidden());

        String objection = JsonPath.read(as("BILLING", post(objectionsOf(invoice)), gloss("GL-5", TODAY,
                        "{\"invoiceLinePosition\":1,\"code\":\"SO3401\",\"amount\":4000}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.uuid");
        change("BILLING", post(OBJECTIONS + "/" + objection + "/decision"), 0, decision(0))
                .andExpect(status().isUnprocessableEntity());
        change("BILLING", post(OBJECTIONS + "/" + objection + "/response"), 0, response("RE9702", 1000))
                .andExpect(status().isBadRequest());
        change("BILLING", post(OBJECTIONS + "/" + objection + "/response"), 0, response("RE2202", 0))
                .andExpect(status().isBadRequest());
        change("BILLING", post(OBJECTIONS + "/" + objection + "/response"), 0, response("RE9602", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedAmount").value(0))
                .andExpect(jsonPath("$.creditNoteNumber").doesNotExist());
        change("BILLING", post(OBJECTIONS + "/" + objection + "/decision"), 1, decision(0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("LIFTED"));
    }

    @Test
    void aLateGlossIsMarkedAndAnUnansweredOneTurnsRed() throws Exception {
        String invoice = filedInvoice("GLOE", delivery, clinical);
        jdbc.update("""
                UPDATE invoice_filings f JOIN invoices i ON i.id = f.invoice_id SET f.filed_on = ?
                WHERE i.uuid = ?""", TODAY.minusDays(60), invoice);

        String objection = JsonPath.read(as("BILLING", post(objectionsOf(invoice)), gloss("GL-9", TODAY.minusDays(40),
                        "{\"invoiceLinePosition\":1,\"code\":\"AU0201\",\"amount\":5000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.extemporaneous").value(false))
                .andExpect(jsonPath("$.state").value("OVERDUE"))
                .andReturn().getResponse().getContentAsString(), "$.uuid");
        as("BILLING", post(objectionsOf(invoice)), gloss("GL-10", TODAY,
                        "{\"invoiceLinePosition\":1,\"code\":\"CL0201\",\"amount\":1000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.extemporaneous").value(true))
                .andExpect(jsonPath("$.state").value("ON_TIME"));

        as("BILLING", get(OBJECTIONS + "/pending").param("state", "OVERDUE"))
                .andExpect(jsonPath("$[?(@.uuid == '" + objection + "')].remainingBusinessDays").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.lessThan(0))));
        as("BILLING", get(OBJECTIONS + "/pending").param("state", "ON_TIME"))
                .andExpect(jsonPath("$[?(@.uuid == '" + objection + "')]").isEmpty());
        change("BILLING", post(OBJECTIONS + "/" + objection + "/response"), 0, response("RE9502", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.respondedLate").value(true));
    }

    private static String objectionsOf(String invoice) {
        return INVOICES + "/" + invoice + "/objections";
    }

    private static String gloss(String record, java.time.LocalDate notifiedOn, String item) {
        return "{\"kind\":\"GLOSS\",\"payerRecord\":\"" + record + "\",\"notifiedOn\":\"" + notifiedOn
                + "\",\"items\":[" + item + "]}";
    }

    private static String response(String code, int accepted) {
        return "{\"responseRecord\":\"RP-1\",\"respondedOn\":\"" + TODAY + "\",\"answers\":[{\"position\":1,"
                + "\"code\":\"" + code + "\",\"acceptedAmount\":" + accepted + ",\"detail\":\"Soporte adjunto\"}]}";
    }

    private static String decision(int upheld) {
        return "{\"decidedOn\":\"" + TODAY + "\",\"rulings\":[{\"position\":1,\"upheldAmount\":" + upheld + "}]}";
    }
}

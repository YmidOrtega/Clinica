package com.ClinicaDeYmid.billing_service;

import com.ClinicaDeYmid.billing_service.application.DianDelivery;
import com.ClinicaDeYmid.billing_service.application.clinical.ClinicalProjection;
import com.ClinicaDeYmid.billing_service.support.MinistrySimulator;
import com.ClinicaDeYmid.billing_service.support.ProducerContract;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvoiceEventsIT extends InvoicingIntegrationTest {

    @Autowired
    private DianDelivery delivery;

    @Autowired
    private ClinicalProjection clinical;

    @Test
    void publishesTheWholeStateOfTheInvoiceAfterEveryChangeSinceItsIssue() throws Exception {
        String invoice = filedInvoice("EVTA", delivery, clinical);
        String objection = JsonPath.read(as("BILLING", post(INVOICES + "/" + invoice + "/objections"), """
                        {"kind":"GLOSS","payerRecord":"GL-EV","notifiedOn":"%s",
                         "items":[{"invoiceLinePosition":1,"code":"TA0201","amount":4000}]}""".formatted(TODAY))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.uuid");
        change("BILLING", post("/api/v1/billing/objections/" + objection + "/response"), 0, """
                {"responseRecord":"RP-EV","respondedOn":"%s",
                 "answers":[{"position":1,"code":"RE9702","acceptedAmount":4000}]}""".formatted(TODAY))
                .andExpect(status().isOk());

        List<Map<String, Object>> events = eventsOf(invoice);

        assertThat(events).extracting(event -> event.get("type")).containsExactly("InvoiceIssued", "InvoiceSigned",
                "InvoiceAcceptedByDian", "InvoiceRipsValidated", "InvoiceFiled", "InvoiceObjectionRegistered",
                "InvoiceCredited", "InvoiceObjectionAnswered");
        assertThat(events).allSatisfy(event -> {
            String payload = (String) event.get("payload");
            assertThat(event.get("aggregatetype")).isEqualTo("billing.invoices");
            assertThat(ProducerContract.INVOICES.breaches(payload)).isEmpty();
            assertThat(payload).doesNotContain("1098765432", "Ana María", "Restrepo");
        });
        String issued = (String) events.getFirst().get("payload");
        assertThat(issued).doesNotContain("\"dian\"", "\"rips\"", "\"filing\"");
        String last = (String) events.getLast().get("payload");
        assertThat((String) JsonPath.read(last, "$.number")).isEqualTo(numberOf(invoice));
        assertThat((String) JsonPath.read(last, "$.buyer.nit")).isEqualTo("900156264-2");
        assertThat((String) JsonPath.read(last, "$.dian.status")).isEqualTo("ACCEPTED");
        assertThat((String) JsonPath.read(last, "$.rips.cuv")).isEqualTo(MinistrySimulator.CUV);
        assertThat((String) JsonPath.read(last, "$.filing.filingNumber")).isEqualTo("RAD-EVTA");
        assertThat((Double) JsonPath.read(last, "$.creditedTotal")).isEqualTo(4000.0);
        assertThat((Double) JsonPath.read(last, "$.balance")).isEqualTo(6000.0);
        assertThat((String) JsonPath.read(last, "$.creditNotes[0].concept")).isEqualTo("PARTIAL_RETURN");
        assertThat((String) JsonPath.read(last, "$.objections[0].status")).isEqualTo("RESPONDED");
        assertThat((Double) JsonPath.read(last, "$.objections[0].acceptedAmount")).isEqualTo(4000.0);

        String copayment = JsonPath.read(last, "$.admissionNumber");
        List<Map<String, Object>> shared = jdbc.queryForList("""
                SELECT CAST(payload AS CHAR) AS payload FROM billing_outbox.outbox_events
                WHERE aggregatetype = 'billing.invoices' AND payload->>'$.admissionNumber' = ?
                  AND payload->>'$.purpose' = 'SHARED_PAYMENT'""", copayment);
        assertThat(shared).isNotEmpty().allSatisfy(event -> {
            String payload = (String) event.get("payload");
            assertThat(ProducerContract.INVOICES.breaches(payload)).isEmpty();
            assertThat((String) JsonPath.read(payload, "$.buyer.kind")).isEqualTo("PATIENT");
            assertThat(payload).doesNotContain("\"nit\"", "1098765432");
        });
    }

    @Test
    void aDraftIsNotPublished() throws Exception {
        anActiveResolution("EVTB");
        Episode episode = outpatient("COVERED");
        String sale = confirmedSale(episode);
        String draft = JsonPath.read(as("BILLING", post(INVOICES), drafting(episode, sale))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.uuid");

        assertThat(eventsOf(draft)).isEmpty();
    }

    private List<Map<String, Object>> eventsOf(String invoice) {
        return jdbc.queryForList("""
                SELECT type, aggregatetype, CAST(payload AS CHAR) AS payload
                FROM billing_outbox.outbox_events WHERE aggregateid = ? AND aggregatetype = 'billing.invoices'
                ORDER BY created_at, id""", invoice);
    }
}

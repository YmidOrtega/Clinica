package com.ClinicaDeYmid.billing_service;

import com.ClinicaDeYmid.billing_service.application.DianDelivery;
import com.ClinicaDeYmid.billing_service.application.clinical.ClinicalProjection;
import com.ClinicaDeYmid.billing_service.application.objection.ObjectionDeadlineAlerts;
import com.ClinicaDeYmid.billing_service.domain.BusinessDeadline;
import com.ClinicaDeYmid.billing_service.domain.PayerObjection;
import com.ClinicaDeYmid.billing_service.support.ProducerContract;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ObjectionAlertsIT extends InvoicingIntegrationTest {

    @Autowired
    private DianDelivery delivery;

    @Autowired
    private ClinicalProjection clinical;

    @Autowired
    private ObjectionDeadlineAlerts alerts;

    @Test
    void publishesAnUnansweredDevolutionOnceWhenItNearsAndWhenItMissesItsDeadline() throws Exception {
        String invoice = filedInvoice("OBAL", delivery, clinical);
        String devolution = JsonPath.read(as("BILLING", post(INVOICES + "/" + invoice + "/objections"), """
                        {"kind":"DEVOLUTION","payerRecord":"DV-9","notifiedOn":"%s","items":[{"code":"DE5002"}]}"""
                        .formatted(TODAY))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.uuid");

        alerts.run();
        assertThat(eventsOf(devolution)).isEmpty();

        jdbc.update("UPDATE payer_objections SET notified_on = ? WHERE uuid = ?", dueSoonNotice(), devolution);
        alerts.run();
        alerts.run();

        List<Map<String, Object>> events = eventsOf(devolution);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.get("type")).isEqualTo("ObjectionResponseDueSoon");
            assertThat(event.get("aggregatetype")).isEqualTo("billing.claim-objections");
        });
        String payload = (String) events.getFirst().get("payload");
        assertThat(ProducerContract.CLAIM_OBJECTIONS.breaches(payload)).isEmpty();
        assertThat((String) JsonPath.read(payload, "$.kind")).isEqualTo("DEVOLUTION");
        assertThat((String) JsonPath.read(payload, "$.payerRecord")).isEqualTo("DV-9");
        assertThat((Integer) JsonPath.read(payload, "$.remainingBusinessDays")).isBetween(0, 3);

        jdbc.update("UPDATE payer_objections SET notified_on = ? WHERE uuid = ?", TODAY.minusDays(20), devolution);
        alerts.run();

        assertThat(eventsOf(devolution)).extracting(event -> event.get("type"))
                .containsExactlyInAnyOrder("ObjectionResponseDueSoon", "ObjectionResponseMissed");
        assertThat(ProducerContract.CLAIM_OBJECTIONS.breaches((String) eventsOf(devolution).getLast().get("payload")))
                .isEmpty();
    }

    private static LocalDate dueSoonNotice() {
        LocalDate day = TODAY;
        while (BusinessDeadline.of(day, PayerObjection.Kind.DEVOLUTION.daysToRespond(), TODAY, 3).state()
                != BusinessDeadline.State.DUE_SOON) {
            day = day.minusDays(1);
        }
        return day;
    }

    private List<Map<String, Object>> eventsOf(String objection) {
        return jdbc.queryForList("""
                SELECT type, aggregatetype, CAST(payload AS CHAR) AS payload
                FROM billing_outbox.outbox_events WHERE aggregateid = ? ORDER BY created_at""", objection);
    }
}

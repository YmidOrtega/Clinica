package com.ClinicaDeYmid.billing_service;

import com.ClinicaDeYmid.billing_service.application.filing.FilingDeadlineAlerts;
import com.ClinicaDeYmid.billing_service.domain.FilingDeadline;
import com.ClinicaDeYmid.billing_service.support.ProducerContract;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FilingAlertsIT extends InvoicingIntegrationTest {

    @Autowired
    private FilingDeadlineAlerts alerts;

    @Test
    void publishesEachInvoiceOnceWhenItNearsAndWhenItMissesItsDeadline() throws Exception {
        anActiveResolution("ALRA");
        String overdue = issued();
        String dueSoon = issued();
        String onTime = issued();
        issuedOn(overdue, TODAY.minusDays(60));
        issuedOn(dueSoon, dueSoonIssueDate());

        alerts.run();
        alerts.run();

        List<Map<String, Object>> overdueEvents = eventsOf(overdue);
        List<Map<String, Object>> dueSoonEvents = eventsOf(dueSoon);
        assertThat(eventsOf(onTime)).isEmpty();
        assertThat(overdueEvents).singleElement().satisfies(event -> {
            assertThat(event.get("type")).isEqualTo("FilingDeadlineMissed");
            assertThat(event.get("aggregatetype")).isEqualTo("billing.filing-deadlines");
        });
        assertThat(dueSoonEvents).singleElement()
                .satisfies(event -> assertThat(event.get("type")).isEqualTo("FilingDeadlineApproaching"));
        String payload = (String) dueSoonEvents.getFirst().get("payload");
        assertThat(ProducerContract.FILING_DEADLINES.breaches(payload)).isEmpty();
        assertThat(ProducerContract.FILING_DEADLINES.breaches((String) overdueEvents.getFirst().get("payload"))).isEmpty();
        assertThat((String) JsonPath.read(payload, "$.invoiceUuid")).isEqualTo(dueSoon);
        assertThat((String) JsonPath.read(payload, "$.state")).isEqualTo("DUE_SOON");
        assertThat((String) JsonPath.read(payload, "$.payerName")).isEqualTo("Nueva EPS S.A.");
        assertThat((Integer) JsonPath.read(payload, "$.remainingBusinessDays")).isBetween(0, 5);
        assertThat((Integer) JsonPath.read((String) overdueEvents.getFirst().get("payload"),
                "$.remainingBusinessDays")).isNegative();

        issuedOn(dueSoon, TODAY.minusDays(60));
        alerts.run();

        assertThat(eventsOf(dueSoon)).extracting(event -> event.get("type"))
                .containsExactlyInAnyOrder("FilingDeadlineApproaching", "FilingDeadlineMissed");
        assertThat(eventsOf(overdue)).hasSize(1);
    }

    private static LocalDate dueSoonIssueDate() {
        LocalDate day = TODAY;
        while (FilingDeadline.of(day, TODAY, 5).state() != FilingDeadline.State.DUE_SOON) {
            day = day.minusDays(1);
        }
        return day;
    }

    private void issuedOn(String invoice, LocalDate day) {
        jdbc.update("UPDATE invoices SET issued_on = ? WHERE uuid = ?", day, invoice);
    }

    private List<Map<String, Object>> eventsOf(String invoice) {
        return jdbc.queryForList("""
                SELECT type, aggregatetype, CAST(payload AS CHAR) AS payload
                FROM billing_outbox.outbox_events WHERE aggregateid = ? ORDER BY created_at""", invoice);
    }
}

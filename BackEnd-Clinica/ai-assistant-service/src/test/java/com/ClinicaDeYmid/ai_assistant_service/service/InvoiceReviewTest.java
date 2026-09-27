package com.ClinicaDeYmid.ai_assistant_service.service;

import com.ClinicaDeYmid.ai_assistant_service.repository.entity.Finding;
import com.ClinicaDeYmid.ai_assistant_service.shared.FindingRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceReviewTest {

    private static final Instant NOW = Instant.parse("2026-09-27T15:00:00Z");
    private static final Duration DIAN_GRACE = Duration.ofHours(2);
    private static final Duration RIPS_GRACE = Duration.ofHours(24);

    @Test
    void aCleanAcceptedAndValidatedInvoiceRaisesNothing() {
        assertThat(rules(invoice("ACCEPTED", NOW.minus(Duration.ofDays(3)), "cuv", null, null, List.of()))).isEmpty();
    }

    @Test
    void theDianIsGivenTimeBeforeAnUnconfirmedInvoiceIsFlagged() {
        assertThat(rules(invoice("AWAITING_VALIDATION", NOW.minus(Duration.ofMinutes(30)), null, null, null, List.of())))
                .isEmpty();
        assertThat(rules(invoice("AWAITING_VALIDATION", NOW.minus(Duration.ofHours(3)), null, null, null, List.of())))
                .containsExactly(FindingRule.DIAN_UNCONFIRMED);
        assertThat(rules(invoice("REJECTED", NOW.minus(Duration.ofMinutes(1)), null, null, null, List.of())))
                .containsExactly(FindingRule.DIAN_REJECTED);
    }

    @Test
    void anAcceptedInvoiceWithoutCuvIsFlaggedOnceItsGraceIsOver() {
        assertThat(rules(invoice("ACCEPTED", NOW.minus(Duration.ofHours(2)), null, null, null, List.of()))).isEmpty();
        assertThat(rules(invoice("ACCEPTED", NOW.minus(Duration.ofHours(30)), null, null, null, List.of())))
                .containsExactly(FindingRule.RIPS_PENDING);
    }

    @Test
    void flagsTheCopayShortfallTheUncontractedCareAndEachRejectedCreditNote() {
        List<InvoiceFacts.CreditNoteFact> notes = List.of(new InvoiceFacts.CreditNoteFact("n1", "NC1", "REJECTED"),
                new InvoiceFacts.CreditNoteFact("n2", "NC2", "ACCEPTED"));

        List<InvoiceReview.Expected> expected = InvoiceReview.of(
                invoice("ACCEPTED", NOW.minus(Duration.ofDays(3)), "cuv", "EMERGENCY", new BigDecimal("3500"), notes),
                NOW, DIAN_GRACE, RIPS_GRACE);

        assertThat(expected).extracting(InvoiceReview.Expected::rule).containsExactlyInAnyOrder(
                FindingRule.CREDIT_NOTE_REJECTED, FindingRule.COPAY_SHORTFALL, FindingRule.BILLED_WITHOUT_CONTRACT);
        assertThat(expected).filteredOn(item -> item.rule() == FindingRule.CREDIT_NOTE_REJECTED)
                .singleElement().satisfies(item -> assertThat(item.subject()).isEqualTo("n1"));
    }

    @Test
    void aVoidedInvoiceHasNothingLeftToReview() {
        InvoiceFacts voided = new InvoiceFacts(UUID.randomUUID(), "SETP1", "SERVICES", "VOIDED", LocalDate.now(),
                "PAYER", "EMERGENCY", new BigDecimal("10"), "REJECTED", NOW, NOW.minus(Duration.ofDays(9)), null,
                false, List.of(), List.of());

        assertThat(InvoiceReview.of(voided, NOW, DIAN_GRACE, RIPS_GRACE)).isEmpty();
        assertThat(InvoiceReview.stillApplies(Finding.open(voided.invoiceUuid(), "SETP1", FindingRule.FILING_OVERDUE,
                null, "x", null, NOW), voided)).isFalse();
    }

    private static List<FindingRule> rules(InvoiceFacts invoice) {
        return InvoiceReview.of(invoice, NOW, DIAN_GRACE, RIPS_GRACE).stream().map(InvoiceReview.Expected::rule).toList();
    }

    private static InvoiceFacts invoice(String dian, Instant since, String cuv, String uncontracted,
                                        BigDecimal shortfall, List<InvoiceFacts.CreditNoteFact> notes) {
        return new InvoiceFacts(UUID.randomUUID(), "SETP990000001", "SERVICES", "ISSUED", LocalDate.of(2026, 9, 1),
                "PAYER", uncontracted, shortfall, dian, since, since, cuv, false, notes, List.of());
    }
}

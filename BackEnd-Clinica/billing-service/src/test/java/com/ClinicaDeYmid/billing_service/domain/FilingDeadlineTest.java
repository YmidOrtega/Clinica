package com.ClinicaDeYmid.billing_service.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilingDeadlineTest {

    private static final LocalDate ISSUED = LocalDate.parse("2026-10-01");
    private static final String CUV = "a2cf8c6b2f9563d39c5cd7dd8d73e77a5926165a269c07ee84a1c4162ecd198526501a93c1b2f8155ddaf88c82fb15b4";

    @Test
    void turnsYellowWithinTheWarningWindowAndRedAfterTheDeadline() {
        FilingDeadline fresh = FilingDeadline.of(ISSUED, ISSUED, 5);
        FilingDeadline dueSoon = FilingDeadline.of(ISSUED, LocalDate.parse("2026-10-27"), 5);
        FilingDeadline lastDay = FilingDeadline.of(ISSUED, LocalDate.parse("2026-11-04"), 5);
        FilingDeadline overdue = FilingDeadline.of(ISSUED, LocalDate.parse("2026-11-09"), 5);

        assertThat(fresh.deadline()).isEqualTo(LocalDate.parse("2026-11-04"));
        assertThat(fresh.state()).isEqualTo(FilingDeadline.State.ON_TIME);
        assertThat(fresh.remainingBusinessDays()).isEqualTo(22);
        assertThat(dueSoon.state()).isEqualTo(FilingDeadline.State.DUE_SOON);
        assertThat(dueSoon.remainingBusinessDays()).isEqualTo(5);
        assertThat(lastDay.state()).isEqualTo(FilingDeadline.State.DUE_SOON);
        assertThat(lastDay.remainingBusinessDays()).isZero();
        assertThat(overdue.state()).isEqualTo(FilingDeadline.State.OVERDUE);
        assertThat(overdue.remainingBusinessDays()).isEqualTo(-3);
    }

    @Test
    void aFilingKeepsTheCuvAndSaysWhenItWasLate() {
        Invoice invoice = InvoiceTest.issuedToThePayer(990000001);
        RipsSubmission validated = RipsSubmission.prepare(invoice, 1, "{}");
        validated.validated(1L, CUV, Instant.now(), false, List.of(), "{}", Instant.now());
        LocalDate validatedOn = invoice.issuedOn();
        LocalDate late = FilingDeadline.deadlineOf(invoice.issuedOn()).plusDays(7);

        InvoiceFiling filing = InvoiceFiling.register(validated, "RAD-1", late, validatedOn, late);

        assertThat(filing.cuv()).isEqualTo(CUV);
        assertThat(filing.late()).isTrue();
        filing.correct("RAD-2", validatedOn, "Fecha mal digitada", validatedOn, late);
        assertThat(filing.late()).isFalse();
        assertThat(filing.correctionReason()).isEqualTo("Fecha mal digitada");
        assertThatThrownBy(() -> filing.correct("RAD-3", validatedOn.minusDays(1), "x", validatedOn, late))
                .isInstanceOf(BillingException.InvalidFilingDate.class);
        assertThatThrownBy(() -> filing.correct("RAD-3", late.plusDays(1), "x", validatedOn, late))
                .isInstanceOf(BillingException.InvalidFilingDate.class);
        assertThatThrownBy(() -> InvoiceFiling.register(RipsSubmission.prepare(invoice, 2, "{}"), "RAD-1", late,
                validatedOn, late)).isInstanceOf(BillingException.FilingWithoutCuv.class);
    }
}

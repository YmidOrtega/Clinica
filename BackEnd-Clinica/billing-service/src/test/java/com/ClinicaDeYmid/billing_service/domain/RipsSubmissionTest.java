package com.ClinicaDeYmid.billing_service.domain;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RipsSubmissionTest {

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-09-27T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final String CUV = "A2CF8C6B2F9563D39C5CD7DD8D73E77A5926165A269C07EE84A1C4162ECD198526501A93C1B2F8155DDAF88C82FB15B4";

    @Test
    void staysPendingWhileTheMinistryDoesNotAnswerAndThenKeepsItsCuv() {
        RipsSubmission submission = RipsSubmission.prepare(issuedInvoice(), 1, "{}");

        submission.unreachable("sin respuesta", NOW.instant());
        submission.validated(1024L, CUV, NOW.instant(), false,
                List.of(new MinistryFinding("NOTIFICACION", "FED078", "Aviso", null, null, "FacturaElectronica")),
                "{\"ResultState\":true}", NOW.instant());

        assertThat(submission.status()).isEqualTo(RipsSubmission.Status.VALIDATED);
        assertThat(submission.cuv()).isEqualTo(CUV.toLowerCase());
        assertThat(submission.attempts()).isEqualTo(2);
        assertThat(submission.lastFailure()).isNull();
        assertThat(submission.findings()).singleElement().satisfies(finding -> assertThat(finding.rejects()).isFalse());
        assertThatThrownBy(() -> submission.rejected(1L, List.of(), "{}", NOW.instant()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aRejectionHasNoCuvAndOnlyAnIssuedInvoiceOfServicesIsSubmitted() {
        RipsSubmission submission = RipsSubmission.prepare(issuedInvoice(), 1, "{}");
        submission.rejected(7L, List.of(new MinistryFinding("RECHAZADO", "RVC019", "Diagnóstico", null, null, "Rips")),
                "{}", NOW.instant());

        assertThat(submission.status()).isEqualTo(RipsSubmission.Status.REJECTED);
        assertThat(submission.cuv()).isNull();
        assertThat(submission.findings().getFirst().rejects()).isTrue();
        assertThatThrownBy(() -> RipsSubmission.prepare(InvoiceTest.copaymentInvoiced(InvoiceTest.account(), "1000", 3, NOW), 1, "{}"))
                .isInstanceOf(BillingException.RipsNotApplicable.class);
        assertThatThrownBy(() -> RipsSubmission.prepare(issuedInvoice(), 1, "{}")
                .validated(1L, "abc", NOW.instant(), false, List.of(), "{}", NOW.instant()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Invoice issuedInvoice() {
        return InvoiceTest.issuedToThePayer(990000001);
    }
}

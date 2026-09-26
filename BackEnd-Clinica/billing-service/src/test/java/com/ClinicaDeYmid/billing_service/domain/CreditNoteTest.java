package com.ClinicaDeYmid.billing_service.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreditNoteTest {

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-09-28T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final UUID CONSULTATION = UUID.randomUUID();

    @Test
    void aVoidCreditsTheWholeInvoiceIncludingTheCopaymentAndVoidsIt() {
        Invoice invoice = issued();

        CreditNote note = CreditNote.issue(invoice, List.of(), CreditConcept.VOID, "Pagador equivocado", List.of(),
                "NC", 7, NOW);
        invoice.credit(note);

        assertThat(note.number()).isEqualTo("NC7");
        assertThat(note.issuedOn()).isEqualTo(LocalDate.parse("2026-09-28"));
        assertThat(note.lines()).extracting(CreditNoteLine::invoiceLinePosition).containsExactly(1, 2);
        assertThat(note.creditedGross()).isEqualByComparingTo("145000");
        assertThat(note.creditedShare()).isEqualByComparingTo("35000");
        assertThat(note.creditedPayable()).isEqualByComparingTo("110000");
        assertThat(invoice.status()).isInstanceOf(InvoiceStatus.Voided.class);
        assertThat(((InvoiceStatus.Voided) invoice.status()).reason()).contains("NC7");
        assertThat(invoice.creditedTotal()).isEqualByComparingTo("110000");
        assertThatThrownBy(() -> CreditNote.issue(invoice, List.of(note), CreditConcept.DISCOUNT, "Otra",
                List.of(new CreditRequest(1, null, BigDecimal.ONE)), "NC", 8, NOW))
                .isInstanceOf(BillingException.InvoiceNotCreditable.class);
    }

    @Test
    void aPartialCreditNeverExceedsWhatEachLineBilled() {
        Invoice invoice = issued();
        CreditNote first = CreditNote.issue(invoice, List.of(), CreditConcept.PRICE_ADJUSTMENT, "Ajuste",
                List.of(new CreditRequest(1, null, new BigDecimal("40000"))), "NC", 1, NOW);
        invoice.credit(first);

        assertThat(first.creditedShare()).isEqualByComparingTo("0");
        assertThat(invoice.status()).isInstanceOf(InvoiceStatus.Issued.class);
        assertThatThrownBy(() -> CreditNote.issue(invoice, List.of(first), CreditConcept.DISCOUNT, "Más",
                List.of(new CreditRequest(1, null, new BigDecimal("5000.01"))), "NC", 2, NOW))
                .isInstanceOf(BillingException.CreditExceedsInvoice.class)
                .hasMessageContaining("línea 1");
        assertThatThrownBy(() -> CreditNote.issue(invoice, List.of(first), CreditConcept.PARTIAL_RETURN, "Devolución",
                List.of(new CreditRequest(1, null, new BigDecimal("5000")), new CreditRequest(2, 1, null)), "NC", 2,
                NOW)).isInstanceOf(BillingException.CreditExceedsInvoice.class).hasMessageContaining("valor a pagar");
        CreditNote second = CreditNote.issue(invoice, List.of(first), CreditConcept.PARTIAL_RETURN, "Devolución",
                List.of(new CreditRequest(1, null, new BigDecimal("5000")), new CreditRequest(2, null,
                        new BigDecimal("65000"))), "NC", 2, NOW);
        invoice.credit(second);
        assertThat(invoice.creditedTotal()).isEqualByComparingTo("110000");
        assertThat(invoice.status()).isInstanceOf(InvoiceStatus.Issued.class);
    }

    @Test
    void refusesMalformedCredits() {
        Invoice invoice = issued();

        assertThatThrownBy(() -> new CreditRequest(1, 1, BigDecimal.TEN)).isInstanceOf(BillingException.InvalidData.class);
        assertThatThrownBy(() -> CreditNote.issue(invoice, List.of(), CreditConcept.DISCOUNT, "x", List.of(), "NC", 1,
                NOW)).isInstanceOf(BillingException.InvalidData.class);
        assertThatThrownBy(() -> CreditNote.issue(invoice, List.of(), CreditConcept.DISCOUNT, "x",
                List.of(new CreditRequest(1, null, BigDecimal.TEN), new CreditRequest(1, null, BigDecimal.ONE)), "NC",
                1, NOW)).isInstanceOf(BillingException.InvalidData.class).hasMessageContaining("repetida");
        assertThatThrownBy(() -> CreditNote.issue(invoice, List.of(), CreditConcept.VOID, "x",
                List.of(new CreditRequest(1, 1, null)), "NC", 1, NOW)).isInstanceOf(BillingException.InvalidData.class);
        assertThatThrownBy(() -> CreditNote.issue(invoice, List.of(), CreditConcept.PARTIAL_RETURN, "x",
                List.of(new CreditRequest(1, 2, null)), "NC", 1, NOW))
                .isInstanceOf(BillingException.CreditExceedsInvoice.class);
    }

    private static Invoice issued() {
        EpisodeAccount account = EpisodeAccount.open(new AdmissionSnapshot(UUID.randomUUID(), "ADM-2026-000123", 1,
                UUID.randomUUID(), AdmissionKind.INPATIENT, AdmissionSnapshot.Status.DISCHARGED, UUID.randomUUID(),
                Instant.parse("2026-09-25T13:00:00Z"), DischargeType.MEDICAL, null));
        Sale sale = Sale.open(account, 1, new SaleType.NonSurgical());
        SaleLine line = sale.charge(new ChargedService(CONSULTATION, "890201", null, "Consulta", null), 1,
                LocalDate.parse("2026-09-26"), new LineOrigin.Manual(), NOW);
        sale.confirm(new PricingTerms(UUID.randomUUID(), "CT-1", UUID.randomUUID(), Map.of(line.uuid(),
                new LinePrice(PriceOrigin.TARIFF_MANUAL, new BigDecimal("45000"), new BigDecimal("45000"), null, null)),
                List.of(new PackageCharge(UUID.randomUUID(), "PAQ-1", "Paquete", new BigDecimal("100000")))), NOW);
        Copayment copayment = new Copayment(UUID.randomUUID(), "AUT-1", new BigDecimal("35000"), null, null,
                Set.of(CONSULTATION), false);
        AccountSummary.Unit unit = AccountSummary.of(account, List.of(sale), true, List.of(copayment), List.of())
                .units().getFirst();
        Invoice invoice = Invoice.draft(unit, account,
                new Buyer(Buyer.Kind.PAYER, UUID.randomUUID(), "NIT", "900156264-2", "Nueva EPS S.A."),
                new HealthUser(UUID.randomUUID(), "CEDULA_DE_CIUDADANIA", "1098765432", "Ana María", "CONTRIBUTORY"));
        invoice.issue(new IssuedNumber(UUID.randomUUID(), "SETP", 990000001), NOW);
        return invoice;
    }
}

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

class InvoiceTest {

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-09-27T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final UUID CONSULTATION = UUID.randomUUID();
    private static final Buyer EPS = new Buyer(Buyer.Kind.PAYER, UUID.randomUUID(), "NIT", "900156264-2", "Nueva EPS S.A.");
    private static final HealthUser ANA = new HealthUser(UUID.randomUUID(), "CEDULA_DE_CIUDADANIA", "1098765432",
            "Ana María Restrepo Gómez", "CONTRIBUTORY");
    static final HealthTerms TERMS = HealthTerms.contracted(PaymentModality.EVENT, CoveragePlan.UPC_CONTRIBUTORY, "a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1");

    @Test
    void aDraftFreezesTheLinesAndSplitsWhatThePayerAndThePatientOwe() {
        EpisodeAccount account = account();
        PackageCharge delivery = new PackageCharge(UUID.randomUUID(), "PAQ-1", "Paquete", new BigDecimal("100000"));
        AccountSummary.Unit unit = unitOf(account, delivery);

        Invoice invoice = Invoice.draft(unit, account, EPS, ANA, TERMS, List.of(copaymentInvoiced(account, "35000", 1, NOW)));

        assertThat(invoice.status()).isInstanceOf(InvoiceStatus.Draft.class);
        assertThat(invoice.number()).isNull();
        assertThat(invoice.lines()).extracting(InvoiceLine::kind)
                .containsExactly(InvoiceLine.Kind.SERVICE, InvoiceLine.Kind.PACKAGE);
        assertThat(invoice.grossTotal()).isEqualByComparingTo("145000");
        assertThat(invoice.patientShare()).isEqualByComparingTo("35000");
        assertThat(invoice.payableTotal()).isEqualByComparingTo("110000");
        assertThat(invoice.stillMatches(unit)).isTrue();
    }

    @Test
    void deductsOnlyWhatWasInvoicedToThePatientAndNeverMoreThanExpected() {
        EpisodeAccount account = account();
        AccountSummary.Unit unit = unitOf(account, null);

        Invoice uncollected = Invoice.draft(unit, account, EPS, ANA, TERMS);
        Invoice partly = Invoice.draft(unit, account, EPS, ANA, TERMS, List.of(copaymentInvoiced(account, "20000", 2, NOW)));

        assertThat(uncollected.patientShare()).isEqualByComparingTo("0");
        assertThat(uncollected.payableTotal()).isEqualByComparingTo("45000");
        assertThat(uncollected.shareShortfall()).isEqualByComparingTo("35000");
        assertThat(partly.payableTotal()).isEqualByComparingTo("25000");
        assertThat(partly.shareShortfall()).isEqualByComparingTo("15000");
        assertThat(partly.sharedPaymentOf(SharedPaymentKind.COPAYMENT)).isEqualByComparingTo("20000");
        assertThatThrownBy(() -> Invoice.draft(unit, account, EPS, ANA, TERMS,
                List.of(copaymentInvoiced(account, "30000", 3, NOW), copaymentInvoiced(account, "10000", 4, NOW))))
                .isInstanceOf(BillingException.SharedPaymentExceedsExpected.class);
        assertThatThrownBy(() -> Invoice.draft(unit, account, EPS, ANA, TERMS,
                List.of(Invoice.sharedPayment(account, new Buyer(Buyer.Kind.PATIENT, UUID.randomUUID(), "CEDULA_DE_CIUDADANIA",
                        "1", "x"), ANA, SharedPaymentKind.COPAYMENT, BigDecimal.TEN, null, "REC-draft", null))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aSharedPaymentIsAnInvoiceToThePatientWithASingleLine() {
        Invoice shared = copaymentInvoiced(account(), "12000", 5, NOW);

        assertThat(shared.purpose()).isEqualTo(Invoice.Purpose.SHARED_PAYMENT);
        assertThat(shared.buyer().kind()).isEqualTo(Buyer.Kind.PATIENT);
        assertThat(shared.payableTotal()).isEqualByComparingTo("12000");
        assertThat(shared.lines()).singleElement().satisfies(line -> {
            assertThat(line.kind()).isEqualTo(InvoiceLine.Kind.SHARED_PAYMENT);
            assertThat(line.code()).isEqualTo("COPAGO");
            assertThat(line.description()).contains("AUT-1");
        });
        assertThat(SharedPaymentKind.proposedFor("CONTRIBUTORY", AdmissionKind.OUTPATIENT))
                .isEqualTo(SharedPaymentKind.MODERATING_FEE);
        assertThat(SharedPaymentKind.proposedFor("CONTRIBUTORY", AdmissionKind.INPATIENT))
                .isEqualTo(SharedPaymentKind.COPAYMENT);
        assertThat(SharedPaymentKind.proposedFor("SUBSIDIZED", AdmissionKind.OUTPATIENT))
                .isEqualTo(SharedPaymentKind.COPAYMENT);
    }

    @Test
    void aPrivatePatientPaysTheWholeInvoiceAsBuyer() {
        EpisodeAccount account = account();
        Buyer patient = new Buyer(Buyer.Kind.PATIENT, ANA.patientUuid(), ANA.documentType(), ANA.documentNumber(), ANA.name());

        Invoice invoice = Invoice.draft(unitOf(account, null), account, patient, ANA, HealthTerms.privatePatient());

        assertThat(invoice.patientShare()).isEqualByComparingTo("0");
        assertThat(invoice.payableTotal()).isEqualByComparingTo(invoice.grossTotal());
    }

    @Test
    void takesItsNumberOnlyWhenIssuedAndThenNeverChanges() {
        EpisodeAccount account = account();
        Invoice invoice = Invoice.draft(unitOf(account, null), account, EPS, ANA, TERMS);
        UUID resolution = UUID.randomUUID();

        invoice.issue(new IssuedNumber(resolution, "SETP", 990000001), NOW);

        assertThat(invoice.number()).isEqualTo("SETP990000001");
        assertThat(invoice.issuedOn()).isEqualTo(LocalDate.parse("2026-09-27"));
        assertThat(invoice.resolutionUuid()).isEqualTo(resolution);
        assertThatThrownBy(() -> invoice.issue(new IssuedNumber(resolution, "SETP", 2), NOW))
                .isInstanceOf(BillingException.InvalidInvoiceTransition.class);
        assertThatThrownBy(() -> invoice.discard("Error", NOW))
                .isInstanceOf(BillingException.InvalidInvoiceTransition.class);
    }

    @Test
    void refusesAUnitThatIsNotReady() {
        EpisodeAccount account = account();
        AccountSummary.Unit notReady = new AccountSummary.Unit(AccountSummary.UnitKind.ACCOUNT, null, List.of(), false,
                "El paciente aún no tiene egreso", Money.ZERO, List.of(), List.of(), Money.ZERO, Money.ZERO,
                AccountSummary.ShareSource.COPAYMENT, List.of(), null, Money.ZERO);

        assertThatThrownBy(() -> Invoice.draft(notReady, account, EPS, ANA, TERMS))
                .isInstanceOf(BillingException.NotABillableUnit.class)
                .hasMessageContaining("egreso");
    }

    @Test
    void noticesWhenTheUnitChangedSinceTheDraft() {
        EpisodeAccount account = account();
        Invoice invoice = Invoice.draft(unitOf(account, null), account, EPS, ANA, TERMS);
        PatientShareAdjustment adjustment = PatientShareAdjustment.of(account, null, BigDecimal.ZERO, "Exento");

        AccountSummary later = AccountSummary.of(account, unitOf(account, null).sales(), true, List.of(), List.of(adjustment));

        assertThat(invoice.stillMatches(later.units().getFirst())).isFalse();
    }

    private static AccountSummary.Unit unitOf(EpisodeAccount account, PackageCharge charge) {
        Sale sale = Sale.open(account, 1, new SaleType.NonSurgical());
        SaleLine line = sale.charge(new ChargedService(CONSULTATION, "890201", null, "Consulta", null), 1,
                LocalDate.parse("2026-09-26"), new LineOrigin.Manual(), NOW);
        sale.confirm(new PricingTerms(UUID.randomUUID(), "CT-1", UUID.randomUUID(), Map.of(line.uuid(),
                new LinePrice(PriceOrigin.TARIFF_MANUAL, new BigDecimal("45000"), new BigDecimal("45000"), null, null)),
                charge == null ? List.of() : List.of(charge)), NOW);
        Copayment copayment = new Copayment(UUID.randomUUID(), "AUT-1", new BigDecimal("35000"), null, null,
                Set.of(CONSULTATION), false);
        return AccountSummary.of(account, List.of(sale), true, List.of(copayment), List.of()).units().getFirst();
    }

    private static EpisodeAccount account() {
        return EpisodeAccount.open(new AdmissionSnapshot(UUID.randomUUID(), "ADM-2026-000123", 1, UUID.randomUUID(),
                AdmissionKind.INPATIENT, AdmissionSnapshot.Status.DISCHARGED, UUID.randomUUID(),
                Instant.parse("2026-09-25T13:00:00Z"), DischargeType.MEDICAL, null));
    }

    static Invoice copaymentInvoiced(EpisodeAccount account, String amount, long consecutive, Clock clock) {
        Invoice shared = Invoice.sharedPayment(account, new Buyer(Buyer.Kind.PATIENT, UUID.randomUUID(),
                        "CEDULA_DE_CIUDADANIA", "1098765432", "Ana María Restrepo"),
                new HealthUser(UUID.randomUUID(), "CEDULA_DE_CIUDADANIA", "1098765432", "Ana María Restrepo",
                        "CONTRIBUTORY"), SharedPaymentKind.COPAYMENT, new BigDecimal(amount), "AUT-1",
                "REC-" + consecutive, "CT-1");
        shared.issue(new IssuedNumber(UUID.randomUUID(), "SETP", consecutive), clock);
        return shared;
    }
}

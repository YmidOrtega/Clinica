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

class AccountSummaryTest {

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-09-27T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final LocalDate TODAY = LocalDate.parse("2026-09-27");
    private static final UUID CONSULTATION = UUID.randomUUID();
    private static final UUID PACKAGE = UUID.randomUUID();

    @Test
    void anInpatientAccountIsOneUnitThatWaitsForTheDischarge() {
        EpisodeAccount account = account(AdmissionKind.INPATIENT, AdmissionSnapshot.Status.ACTIVE);
        Sale sale = confirmed(account, 1, "100000", List.of());

        AccountSummary summary = AccountSummary.of(account, List.of(sale), true, List.of(), List.of());

        assertThat(summary.units()).singleElement().satisfies(unit -> {
            assertThat(unit.kind()).isEqualTo(AccountSummary.UnitKind.ACCOUNT);
            assertThat(unit.ready()).isFalse();
            assertThat(unit.notReadyReason()).contains("egreso");
        });
    }

    @Test
    void aDraftSaleHoldsBackTheDischargedAccount() {
        EpisodeAccount account = account(AdmissionKind.INPATIENT, AdmissionSnapshot.Status.DISCHARGED);
        Sale draft = Sale.open(account, 2, new SaleType.NonSurgical());

        AccountSummary summary = AccountSummary.of(account, List.of(confirmed(account, 1, "100000", List.of()), draft),
                true, List.of(), List.of());

        assertThat(summary.units().getFirst().ready()).isFalse();
        assertThat(summary.units().getFirst().notReadyReason()).contains("borrador");
    }

    @Test
    void aPackageRepeatedInTwoSalesIsChargedOnce() {
        EpisodeAccount account = account(AdmissionKind.INPATIENT, AdmissionSnapshot.Status.DISCHARGED);
        PackageCharge delivery = new PackageCharge(PACKAGE, "PAQ-PARTO", "Parto", new BigDecimal("1800000"));

        AccountSummary summary = AccountSummary.of(account, List.of(
                confirmed(account, 1, "0", List.of(delivery)), confirmed(account, 2, "20000", List.of(delivery))),
                true, List.of(), List.of());

        AccountSummary.Unit unit = summary.units().getFirst();
        assertThat(unit.ready()).isTrue();
        assertThat(unit.packages()).containsExactly(delivery);
        assertThat(unit.duplicatePackages()).containsExactly(delivery);
        assertThat(unit.total()).isEqualByComparingTo("1820000");
    }

    @Test
    void anOutpatientEpisodeIsInvoicedSaleBySaleAndEachCopaymentOnce() {
        EpisodeAccount account = account(AdmissionKind.OUTPATIENT, AdmissionSnapshot.Status.ACTIVE);
        Copayment copayment = new Copayment(UUID.randomUUID(), "AUT-1", new BigDecimal("35000"), null, null,
                Set.of(CONSULTATION), false);

        AccountSummary summary = AccountSummary.of(account, List.of(
                confirmed(account, 1, "45000", List.of()), confirmed(account, 2, "45000", List.of())), true,
                List.of(copayment), List.of());

        assertThat(summary.units()).hasSize(2).allMatch(AccountSummary.Unit::ready);
        assertThat(summary.units()).extracting(AccountSummary.Unit::patientShare)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("35000"), BigDecimal.ZERO);
        assertThat(summary.payerShare()).isEqualByComparingTo("55000");
    }

    @Test
    void theCopaymentNeverExceedsWhatTheUnitCosts() {
        EpisodeAccount account = account(AdmissionKind.OUTPATIENT, AdmissionSnapshot.Status.ACTIVE);
        Copayment copayment = new Copayment(UUID.randomUUID(), "AUT-1", new BigDecimal("90000"), null, null, Set.of(), true);

        AccountSummary summary = AccountSummary.of(account, List.of(confirmed(account, 1, "45000", List.of())), true,
                List.of(copayment), List.of());

        assertThat(summary.units().getFirst().patientShare()).isEqualByComparingTo("45000");
    }

    @Test
    void aPatientWithoutCoveragePaysEverything() {
        EpisodeAccount account = account(AdmissionKind.OUTPATIENT, AdmissionSnapshot.Status.ACTIVE);

        AccountSummary summary = AccountSummary.of(account, List.of(confirmed(account, 1, "45000", List.of())), false,
                List.of(), List.of());

        assertThat(summary.units().getFirst().shareSource()).isEqualTo(AccountSummary.ShareSource.PRIVATE);
        assertThat(summary.units().getFirst().payerShare()).isEqualByComparingTo("0");
    }

    @Test
    void anAuditedAdjustmentReplacesTheCopayment() {
        EpisodeAccount account = account(AdmissionKind.INPATIENT, AdmissionSnapshot.Status.DISCHARGED);
        PatientShareAdjustment adjustment = PatientShareAdjustment.of(account, null, new BigDecimal("12000"),
                "Cuota moderadora del nivel 2");

        AccountSummary summary = AccountSummary.of(account, List.of(confirmed(account, 1, "100000", List.of())), true,
                List.of(), List.of(adjustment));

        assertThat(summary.units().getFirst().shareSource()).isEqualTo(AccountSummary.ShareSource.ADJUSTED);
        assertThat(summary.units().getFirst().patientShare()).isEqualByComparingTo("12000");
        assertThat(summary.units().getFirst().payerShare()).isEqualByComparingTo("88000");
    }

    @Test
    void aVoidedAccountHasNothingToInvoice() {
        EpisodeAccount account = account(AdmissionKind.INPATIENT, AdmissionSnapshot.Status.CANCELLED);

        assertThat(AccountSummary.of(account, List.of(), true, List.of(), List.of()).units()).isEmpty();
    }

    private static Sale confirmed(EpisodeAccount account, int sequence, String lineTotal, List<PackageCharge> packages) {
        Sale sale = Sale.open(account, sequence, new SaleType.NonSurgical());
        SaleLine line = sale.charge(new ChargedService(CONSULTATION, "890201", null, "Consulta", null), 1, TODAY,
                new LineOrigin.Manual(), NOW);
        BigDecimal total = new BigDecimal(lineTotal);
        sale.confirm(new PricingTerms(UUID.randomUUID(), "CT", UUID.randomUUID(), Map.of(line.uuid(),
                new LinePrice(total.signum() == 0 ? PriceOrigin.PACKAGE : PriceOrigin.TARIFF_MANUAL, total, total,
                        null, null)), packages), NOW);
        return sale;
    }

    private static EpisodeAccount account(AdmissionKind kind, AdmissionSnapshot.Status status) {
        return EpisodeAccount.open(new AdmissionSnapshot(UUID.randomUUID(), "ADM-2026-000123", 1, UUID.randomUUID(), kind,
                status, UUID.randomUUID(), Instant.parse("2026-09-25T13:00:00Z"),
                status == AdmissionSnapshot.Status.DISCHARGED ? DischargeType.MEDICAL : null,
                status == AdmissionSnapshot.Status.CANCELLED ? "Duplicado" : null));
    }
}

package com.ClinicaDeYmid.billing_service.domain;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SaleTest {

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-09-27T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final LocalDate TODAY = LocalDate.parse("2026-09-27");
    private static final ChargedService CONSULTATION =
            new ChargedService(UUID.randomUUID(), "890201", "CON-001", "Consulta de primera vez por medicina general", "CONSULTATION");

    @Test
    void isNumberedAfterTheAdmission() {
        Sale sale = Sale.open(account(AdmissionSnapshot.Status.ACTIVE), 3, new SaleType.NonSurgical());

        assertThat(sale.number()).isEqualTo("ADM-2026-000123-V03");
        assertThat(sale.status()).isInstanceOf(SaleStatus.Draft.class);
        assertThat(sale.type()).isInstanceOf(SaleType.NonSurgical.class);
    }

    @Test
    void aVoidedAccountTakesNoSales() {
        assertThatThrownBy(() -> Sale.open(account(AdmissionSnapshot.Status.CANCELLED), 1, new SaleType.NonSurgical()))
                .isInstanceOf(BillingException.AccountClosedForCharges.class);
    }

    @Test
    void aDischargedEpisodeStillTakesLateCharges() {
        Sale sale = Sale.open(account(AdmissionSnapshot.Status.DISCHARGED), 1, new SaleType.NonSurgical());

        SaleLine line = sale.charge(CONSULTATION, 1, TODAY, new LineOrigin.Manual(), NOW);
        sale.confirm(tariff(line, "45000.00"), NOW);

        assertThat(sale.status()).isInstanceOf(SaleStatus.Confirmed.class);
    }

    @Test
    void chargesLinesInOrderAndRemembersWhereTheyCameFrom() {
        Sale sale = draft();
        UUID authorization = UUID.randomUUID();

        sale.charge(CONSULTATION, 2, TODAY, new LineOrigin.Manual(), NOW);
        sale.charge(CONSULTATION, 1, TODAY.minusDays(1), new LineOrigin.Authorized(authorization, "AUT-1"), NOW);

        assertThat(sale.lines()).extracting(SaleLine::position).containsExactly(1, 2);
        assertThat(sale.charges(CONSULTATION.portfolioItemUuid(), authorization)).isTrue();
        assertThat(sale.lines().get(1).origin()).isEqualTo(new LineOrigin.Authorized(authorization, "AUT-1"));
    }

    @Test
    void refusesImpossibleQuantitiesAndDates() {
        Sale sale = draft();

        assertThatThrownBy(() -> sale.charge(CONSULTATION, 0, TODAY, new LineOrigin.Manual(), NOW))
                .isInstanceOf(BillingException.InvalidData.class);
        assertThatThrownBy(() -> sale.charge(CONSULTATION, 1000, TODAY, new LineOrigin.Manual(), NOW))
                .isInstanceOf(BillingException.InvalidData.class);
        assertThatThrownBy(() -> sale.charge(CONSULTATION, 1, TODAY.plusDays(1), new LineOrigin.Manual(), NOW))
                .isInstanceOf(BillingException.InvalidData.class).hasMessageContaining("posterior a hoy");
        assertThatThrownBy(() -> sale.charge(CONSULTATION, 1, LocalDate.parse("2026-09-24"), new LineOrigin.Manual(), NOW))
                .isInstanceOf(BillingException.InvalidData.class).hasMessageContaining("ingreso");
    }

    @Test
    void aRemovedLineStaysVisibleButNoLongerCounts() {
        Sale sale = draft();
        SaleLine line = sale.charge(CONSULTATION, 1, TODAY, new LineOrigin.Manual(), NOW);

        sale.removeLine(line.uuid(), "Cargado por error", NOW);

        assertThat(sale.lines()).hasSize(1);
        assertThat(sale.activeLines()).isEmpty();
        assertThat(line.removalReason()).isEqualTo("Cargado por error");
        assertThatThrownBy(() -> sale.removeLine(line.uuid(), "Otra vez", NOW))
                .isInstanceOf(BillingException.LineAlreadyRemoved.class);
        assertThatThrownBy(() -> sale.removeLine(UUID.randomUUID(), "No existe", NOW))
                .isInstanceOf(BillingException.LineNotFound.class);
    }

    @Test
    void cannotConfirmWithoutLines() {
        Sale sale = draft();
        SaleLine line = sale.charge(CONSULTATION, 1, TODAY, new LineOrigin.Manual(), NOW);
        sale.removeLine(line.uuid(), "Cargado por error", NOW);

        assertThatThrownBy(() -> sale.confirm(PricingTerms.withoutContract(), NOW))
                .isInstanceOf(BillingException.EmptySale.class);
    }

    @Test
    void aConfirmedSaleNoLongerChangesButCanBeCancelledOnce() {
        Sale sale = draft();
        SaleLine line = sale.charge(CONSULTATION, 1, TODAY, new LineOrigin.Manual(), NOW);
        sale.confirm(tariff(line, "45000.00"), NOW);

        assertThatThrownBy(() -> sale.charge(CONSULTATION, 1, TODAY, new LineOrigin.Manual(), NOW))
                .isInstanceOf(BillingException.SaleNotEditable.class);
        assertThatThrownBy(() -> sale.removeLine(line.uuid(), "Tarde", NOW))
                .isInstanceOf(BillingException.SaleNotEditable.class);
        assertThatThrownBy(() -> sale.confirm(tariff(line, "45000.00"), NOW))
                .isInstanceOf(BillingException.InvalidSaleTransition.class);

        sale.cancel("El paciente no recibió el servicio", NOW);

        assertThat(sale.status()).isEqualTo(new SaleStatus.Cancelled("El paciente no recibió el servicio", NOW.instant()));
        assertThatThrownBy(() -> sale.cancel("Otra vez", NOW)).isInstanceOf(BillingException.InvalidSaleTransition.class);
    }

    @Test
    void anAccountHoldsAtMostNinetyNineSales() {
        assertThatThrownBy(() -> Sale.open(account(AdmissionSnapshot.Status.ACTIVE), 100, new SaleType.NonSurgical()))
                .isInstanceOf(BillingException.TooManySales.class);
    }

    private static PricingTerms tariff(SaleLine line, String unitPrice) {
        java.math.BigDecimal unit = new java.math.BigDecimal(unitPrice);
        return new PricingTerms(UUID.randomUUID(), "CT-1", UUID.randomUUID(), java.util.Map.of(line.uuid(),
                new LinePrice(PriceOrigin.TARIFF_MANUAL, unit, Money.times(unit, line.quantity()), null, "ISS2001")),
                java.util.List.of());
    }

    private static Sale draft() {
        return Sale.open(account(AdmissionSnapshot.Status.ACTIVE), 1, new SaleType.NonSurgical());
    }

    private static EpisodeAccount account(AdmissionSnapshot.Status status) {
        return EpisodeAccount.open(new AdmissionSnapshot(UUID.randomUUID(), "ADM-2026-000123", 1, UUID.randomUUID(),
                AdmissionKind.OUTPATIENT, status, UUID.randomUUID(), Instant.parse("2026-09-25T13:00:00Z"),
                status == AdmissionSnapshot.Status.DISCHARGED ? DischargeType.MEDICAL : null,
                status == AdmissionSnapshot.Status.CANCELLED ? "Duplicado" : null));
    }
}

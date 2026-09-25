package com.ClinicaDeYmid.billing_service.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalePricingTest {

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-09-27T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final LocalDate TODAY = LocalDate.parse("2026-09-27");
    private static final UUID CONTRACT = UUID.randomUUID();
    private static final UUID PACKAGE = UUID.randomUUID();

    @Test
    void confirmingFreezesTheContractPricesAndTheTotals() {
        Sale sale = draft();
        SaleLine consultation = charge(sale, "890201", 2);
        SaleLine xray = charge(sale, "871121", 1);

        sale.confirm(terms(Map.of(
                consultation.uuid(), priced(PriceOrigin.TARIFF_MANUAL, "45000.00", 2),
                xray.uuid(), priced(PriceOrigin.CONTRACT_EXCEPTION, "80000.50", 1)), List.of()), NOW);

        assertThat(consultation.price()).get().extracting(LinePrice::lineTotal).isEqualTo(new BigDecimal("90000.00"));
        assertThat(sale.settlement()).get().satisfies(settled -> {
            assertThat(settled.contractUuid()).isEqualTo(CONTRACT);
            assertThat(settled.linesTotal()).isEqualTo(new BigDecimal("170000.50"));
            assertThat(settled.total()).isEqualTo(new BigDecimal("170000.50"));
        });
    }

    @Test
    void aPackageIsChargedOnceAndItsServicesGoInZero() {
        Sale sale = draft();
        SaleLine delivery = charge(sale, "735301", 1);
        SaleLine stay = charge(sale, "109101", 2);
        PackageCharge agreed = new PackageCharge(PACKAGE, "PAQ-PARTO", "Parto vaginal", new BigDecimal("1800000"));

        sale.confirm(terms(Map.of(
                delivery.uuid(), covered(PriceOrigin.PACKAGE),
                stay.uuid(), covered(PriceOrigin.PACKAGE)), List.of(agreed)), NOW);

        assertThat(sale.packages()).extracting(SalePackage::charge).containsExactly(agreed);
        assertThat(sale.settlement()).get().satisfies(settled -> {
            assertThat(settled.linesTotal()).isEqualTo(Money.ZERO);
            assertThat(settled.packagesTotal()).isEqualTo(new BigDecimal("1800000.00"));
            assertThat(settled.total()).isEqualTo(new BigDecimal("1800000.00"));
        });
    }

    @Test
    void capitatedServicesStayInZeroAsInformation() {
        Sale sale = draft();
        SaleLine consultation = charge(sale, "890201", 1);

        sale.confirm(terms(Map.of(consultation.uuid(), covered(PriceOrigin.CAPITATION)), List.of()), NOW);

        assertThat(consultation.price()).get().satisfies(price -> {
            assertThat(price.origin()).isEqualTo(PriceOrigin.CAPITATION);
            assertThat(price.origin().billablePerService()).isFalse();
            assertThat(price.lineTotal()).isEqualTo(Money.ZERO);
        });
    }

    @Test
    void aServiceWithoutTariffBlocksTheConfirmationUntilItIsPricedByHand() {
        Sale sale = draft();
        SaleLine rare = charge(sale, "999001", 3);
        PricingTerms terms = terms(Map.of(rare.uuid(), LinePrice.unpriced()), List.of());

        assertThat(sale.price(terms).pending()).containsExactly(rare);
        assertThatThrownBy(() -> sale.confirm(terms, NOW))
                .isInstanceOf(BillingException.UnpricedLines.class)
                .hasMessageContaining("999001");

        sale.priceManually(rare.uuid(), new BigDecimal("12500"), "Tarifa institucional acordada con el pagador", NOW);
        sale.confirm(terms, NOW);

        assertThat(rare.price()).get().satisfies(price -> {
            assertThat(price.origin()).isEqualTo(PriceOrigin.MANUAL);
            assertThat(price.lineTotal()).isEqualTo(new BigDecimal("37500.00"));
        });
    }

    @Test
    void aContractPriceAlwaysWinsOverAManualOne() {
        Sale sale = draft();
        SaleLine consultation = charge(sale, "890201", 1);
        sale.priceManually(consultation.uuid(), new BigDecimal("99999"), "Se creyó sin tarifa", NOW);

        PricedSale priced = sale.price(terms(Map.of(consultation.uuid(),
                priced(PriceOrigin.TARIFF_MANUAL, "45000.00", 1)), List.of()));

        assertThat(priced.lines().get(consultation.uuid()).origin()).isEqualTo(PriceOrigin.TARIFF_MANUAL);
    }

    @Test
    void withoutContractEveryLineNeedsAManualPrice() {
        Sale sale = draft();
        SaleLine consultation = charge(sale, "890201", 1);

        assertThat(sale.price(PricingTerms.withoutContract()).pending()).containsExactly(consultation);
    }

    @Test
    void refusesManualPricesThatAreNotMoney() {
        Sale sale = draft();
        SaleLine line = charge(sale, "890201", 1);

        assertThatThrownBy(() -> sale.priceManually(line.uuid(), BigDecimal.ZERO, "Motivo", NOW))
                .isInstanceOf(BillingException.InvalidData.class);
        assertThatThrownBy(() -> sale.priceManually(line.uuid(), new BigDecimal("10.001"), "Motivo", NOW))
                .isInstanceOf(BillingException.InvalidData.class);
        assertThatThrownBy(() -> sale.priceManually(line.uuid(), new BigDecimal("100"), " ", NOW))
                .isInstanceOf(BillingException.InvalidData.class);
    }

    private static PricingTerms terms(Map<UUID, LinePrice> quoted, List<PackageCharge> packages) {
        return new PricingTerms(CONTRACT, "CT-2026-001", UUID.randomUUID(), quoted, packages);
    }

    private static LinePrice priced(PriceOrigin origin, String unit, int quantity) {
        BigDecimal price = new BigDecimal(unit);
        return new LinePrice(origin, price, Money.times(price, quantity), UUID.randomUUID(), "ISS2001");
    }

    private static LinePrice covered(PriceOrigin origin) {
        return new LinePrice(origin, Money.ZERO, Money.ZERO, PACKAGE, null);
    }

    private static SaleLine charge(Sale sale, String cups, int quantity) {
        return sale.charge(new ChargedService(UUID.randomUUID(), cups, null, "Servicio " + cups, null), quantity,
                TODAY, new LineOrigin.Manual(), NOW);
    }

    private static Sale draft() {
        return Sale.open(EpisodeAccount.open(new AdmissionSnapshot(UUID.randomUUID(), "ADM-2026-000123", 1,
                UUID.randomUUID(), AdmissionKind.OUTPATIENT, AdmissionSnapshot.Status.ACTIVE, UUID.randomUUID(),
                Instant.parse("2026-09-25T13:00:00Z"), null, null)), 1, new SaleType.NonSurgical());
    }
}

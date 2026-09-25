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

class SurgicalSaleTest {

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-09-27T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final LocalDate SURGERY = LocalDate.parse("2026-09-26");
    private static final ChargedService CHOLECYSTECTOMY =
            new ChargedService(UUID.randomUUID(), "514201", null, "Colecistectomía", "SURGERY");
    private static final ChargedService HERNIA =
            new ChargedService(UUID.randomUUID(), "530101", null, "Herniorrafia inguinal", "SURGERY");
    private static final TeamMember SURGEON = new TeamMember(SurgicalRole.SURGEON, UUID.randomUUID(), "Ana Cirujana", "RM-1");
    private static final TeamMember ANESTHESIOLOGIST =
            new TeamMember(SurgicalRole.ANESTHESIOLOGIST, UUID.randomUUID(), "Luis Anestesia", "RM-2");

    @Test
    void chargesEachProcedureOnceOnTheDayOfTheSurgeryWithItsRoute() {
        Sale sale = surgical();

        SaleLine line = sale.chargeProcedure(CHOLECYSTECTOMY, " abdominal ", new LineOrigin.Manual(), NOW);

        assertThat(line.kind()).isEqualTo(LineKind.PROCEDURE);
        assertThat(line.quantity()).isEqualTo(1);
        assertThat(line.serviceDate()).isEqualTo(SURGERY);
        assertThat(line.route()).isEqualTo("ABDOMINAL");
        assertThat(sale.chargeProcedure(HERNIA, null, new LineOrigin.Manual(), NOW).route()).isEqualTo("UNICA");
    }

    @Test
    void proceduresAndATeamOnlyBelongToASurgicalSale() {
        Sale sale = Sale.open(account(), 1, new SaleType.NonSurgical());

        assertThatThrownBy(() -> sale.chargeProcedure(CHOLECYSTECTOMY, "A", new LineOrigin.Manual(), NOW))
                .isInstanceOf(BillingException.ProcedureOutsideSurgicalSale.class);
        assertThatThrownBy(() -> sale.assignTeam(List.of(SURGEON), NOW))
                .isInstanceOf(BillingException.ProcedureOutsideSurgicalSale.class);
    }

    @Test
    void eachRoleAppearsOnceInTheTeam() {
        Sale sale = surgical();

        assertThatThrownBy(() -> sale.assignTeam(List.of(SURGEON, SURGEON), NOW))
                .isInstanceOf(BillingException.InvalidData.class);
    }

    @Test
    void confirmsWithTheLiquidationOfEveryComponent() {
        Sale sale = surgical();
        SaleLine line = sale.chargeProcedure(CHOLECYSTECTOMY, "ABDOMINAL", new LineOrigin.Manual(), NOW);
        sale.assignTeam(List.of(SURGEON, ANESTHESIOLOGIST), NOW);

        sale.confirm(terms(line, liquidation(true)), NOW);

        assertThat(line.price()).get().satisfies(price -> {
            assertThat(price.origin()).isEqualTo(PriceOrigin.SURGICAL_LIQUIDATION);
            assertThat(price.surgical().components()).hasSize(3);
            assertThat(price.lineTotal()).isEqualByComparingTo("395300.00");
        });
        assertThat(sale.settlement()).get().extracting(Sale.Settlement::total).isEqualTo(new BigDecimal("395300.00"));
    }

    @Test
    void theTeamMustCoverEveryFeeTheLiquidationCharges() {
        Sale sale = surgical();
        SaleLine line = sale.chargeProcedure(CHOLECYSTECTOMY, "ABDOMINAL", new LineOrigin.Manual(), NOW);
        sale.assignTeam(List.of(SURGEON), NOW);

        assertThatThrownBy(() -> sale.confirm(terms(line, liquidation(true)), NOW))
                .isInstanceOf(BillingException.SurgicalTeamIncomplete.class)
                .hasMessageContaining("ANESTHESIOLOGIST");

        Sale withoutSurgeon = surgical();
        SaleLine other = withoutSurgeon.chargeProcedure(CHOLECYSTECTOMY, "ABDOMINAL", new LineOrigin.Manual(), NOW);
        assertThatThrownBy(() -> withoutSurgeon.confirm(terms(other, liquidation(false)), NOW))
                .isInstanceOf(BillingException.SurgicalTeamIncomplete.class)
                .hasMessageContaining("SURGEON");
    }

    @Test
    void aSurgeryCannotHideInANonSurgicalSale() {
        Sale sale = Sale.open(account(), 1, new SaleType.NonSurgical());
        SaleLine line = sale.charge(CHOLECYSTECTOMY, 1, SURGERY, new LineOrigin.Manual(), NOW);
        PricingTerms terms = new PricingTerms(UUID.randomUUID(), "CT", UUID.randomUUID(),
                Map.of(line.uuid(), LinePrice.unpriced()), List.of(), Set.of(), AuthorizationEvidence.none(),
                Set.of(line.uuid()));

        assertThat(sale.price(terms, NOW.getZone()).misplacedSurgeries()).containsExactly(line);
        assertThat(sale.price(terms, NOW.getZone()).pending()).isEmpty();
        assertThatThrownBy(() -> sale.confirm(terms, NOW))
                .isInstanceOf(BillingException.SurgeryNeedsSurgicalSale.class)
                .hasMessageContaining("514201");
    }

    private static SurgicalDetail liquidation(boolean withAnesthesia) {
        List<ComponentCharge> charges = withAnesthesia
                ? List.of(charge(SurgicalComponent.SURGEON, "139700"), charge(SurgicalComponent.ANESTHESIOLOGIST, "105600"),
                        charge(SurgicalComponent.OPERATING_ROOM, "150000"))
                : List.of(charge(SurgicalComponent.SURGEON, "139700"));
        return new SurgicalDetail(1, true, false, new BigDecimal("110"), charges);
    }

    private static ComponentCharge charge(SurgicalComponent component, String amount) {
        return new ComponentCharge(component, new BigDecimal(amount), new BigDecimal("100"), new BigDecimal(amount));
    }

    private static PricingTerms terms(SaleLine line, SurgicalDetail detail) {
        return new PricingTerms(UUID.randomUUID(), "CT", UUID.randomUUID(), Map.of(line.uuid(),
                new LinePrice(PriceOrigin.SURGICAL_LIQUIDATION, detail.total(), detail.total(), null, "ISS", detail)),
                List.of());
    }

    private static Sale surgical() {
        return Sale.open(account(), 1, new SaleType.Surgical(SURGERY));
    }

    private static EpisodeAccount account() {
        return EpisodeAccount.open(new AdmissionSnapshot(UUID.randomUUID(), "ADM-2026-000123", 1, UUID.randomUUID(),
                AdmissionKind.INPATIENT, AdmissionSnapshot.Status.ACTIVE, UUID.randomUUID(),
                Instant.parse("2026-09-25T13:00:00Z"), null, null));
    }
}

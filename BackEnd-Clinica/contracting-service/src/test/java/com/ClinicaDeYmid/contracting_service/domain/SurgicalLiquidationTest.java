package com.ClinicaDeYmid.contracting_service.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SurgicalLiquidationTest {

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-03-01T15:00:00Z"), ZoneOffset.UTC);

    private final TariffManualVersion version = TariffManualVersion.draft(
            TariffManual.of("ISS_SINT", "Manual sintético", PriceUnit.COP), "2026", BigDecimal.ONE,
            LocalDate.parse("2026-01-01"));

    @Test
    void theMajorProcedureGoesInFullAndTheOthersByRoute() {
        SurgicalRuleSet rules = issLikeRules();
        List<SurgicalLiquidation.Liquidated> liquidated = SurgicalLiquidation.liquidate(rules, BigDecimal.ONE, List.of(
                new SurgicalLiquidation.Procedure(0, item("512101", "20"), "PIEL"),
                new SurgicalLiquidation.Procedure(1, item("514201", "110"), "ABDOMINAL"),
                new SurgicalLiquidation.Procedure(2, item("530101", "60"), "ABDOMINAL")));

        assertThat(liquidated).extracting(SurgicalLiquidation.Liquidated::requestIndex).containsExactly(1, 2, 0);
        assertThat(liquidated.get(0).principal()).isTrue();
        assertThat(liquidated.get(0).total()).isEqualByComparingTo("484900");
        assertThat(liquidated.get(1).sameRoute()).isTrue();
        assertThat(liquidated.get(1).total()).isEqualByComparingTo("177700");
        assertThat(liquidated.get(2).sameRoute()).isFalse();
        assertThat(liquidated.get(2).total()).isEqualByComparingTo("70950");
    }

    @Test
    void theAssistantOnlyAppearsFromItsMinimumBasis() {
        List<SurgicalLiquidation.Liquidated> liquidated = SurgicalLiquidation.liquidate(issLikeRules(), BigDecimal.ONE,
                List.of(new SurgicalLiquidation.Procedure(0, item("512101", "20"), "PIEL")));

        assertThat(liquidated.getFirst().components()).extracting(SurgicalLiquidation.ComponentCharge::component)
                .containsExactlyInAnyOrder(SurgicalComponent.SURGEON, SurgicalComponent.ANESTHESIOLOGIST,
                        SurgicalComponent.OPERATING_ROOM, SurgicalComponent.MATERIALS);
    }

    @Test
    void appliesTheFactorOfTheContractToEveryComponent() {
        List<SurgicalLiquidation.Liquidated> liquidated = SurgicalLiquidation.liquidate(issLikeRules(),
                new BigDecimal("1.1"), List.of(new SurgicalLiquidation.Procedure(0, item("514201", "110"), "A")));

        assertThat(liquidated.getFirst().components().getFirst().fullValue()).isEqualByComparingTo("153670.00");
        assertThat(liquidated.getFirst().total()).isEqualByComparingTo("533390.00");
    }

    @Test
    void aBasisOutsideTheRangesIsADataGapNotAZero() {
        assertThatThrownBy(() -> SurgicalLiquidation.liquidate(issLikeRules(), BigDecimal.ONE,
                List.of(new SurgicalLiquidation.Procedure(0, item("514201", "500"), "A"))))
                .isInstanceOf(ContractingException.SurgicalRulesDoNotCover.class);
    }

    @Test
    void refusesRulesThatCannotBeApplied() {
        assertThatThrownBy(() -> SurgicalRuleSet.load(version, SurgicalBasis.UVR, List.of(
                perUnit(SurgicalComponent.ANESTHESIOLOGIST, "960", null)), checksum(), null, NOW))
                .hasMessageContaining("cirujano");
        assertThatThrownBy(() -> SurgicalRuleSet.load(version, SurgicalBasis.UVR, List.of(
                perUnit(SurgicalComponent.SURGEON, "1270", null), perUnit(SurgicalComponent.SURGEON, "1300", null)),
                checksum(), null, NOW)).hasMessageContaining("dos veces");
        assertThatThrownBy(() -> SurgicalRuleSet.load(version, SurgicalBasis.UVR, List.of(
                perUnit(SurgicalComponent.SURGEON, "1270", null),
                byRange(SurgicalComponent.OPERATING_ROOM, range("0", "50", "30000"), range("40", "90", "60000"))),
                checksum(), null, NOW)).hasMessageContaining("se cruzan");
        assertThatThrownBy(() -> SurgicalRuleSet.load(version, SurgicalBasis.UVR, List.of(
                new SurgicalComponentRule.Definition(SurgicalComponent.SURGEON, SurgicalComponentRule.Mode.PER_UNIT,
                        new BigDecimal("1270"), null, null, new BigDecimal("120"), BigDecimal.TEN)),
                checksum(), null, NOW)).hasMessageContaining("entre 0 y 100");
    }

    private SurgicalRuleSet issLikeRules() {
        return SurgicalRuleSet.load(version, SurgicalBasis.UVR, List.of(
                perUnit(SurgicalComponent.SURGEON, "1270", null),
                perUnit(SurgicalComponent.ANESTHESIOLOGIST, "960", null),
                perUnit(SurgicalComponent.ASSISTANT, "360", "30"),
                byRange(SurgicalComponent.OPERATING_ROOM, range("0", "20", "30000"), range("20.01", "50", "60000"),
                        range("50.01", "450", "150000")),
                byRange(SurgicalComponent.MATERIALS, range("0", "50", "20000"), range("50.01", "450", "50000"))),
                checksum(), null, NOW);
    }

    private TariffItem item(String cups, String basis) {
        return TariffItem.of(version, cups, "Procedimiento " + cups, BigDecimal.ZERO, new BigDecimal(basis));
    }

    static SurgicalComponentRule.Definition perUnit(SurgicalComponent component, String rate, String minimum) {
        return new SurgicalComponentRule.Definition(component, SurgicalComponentRule.Mode.PER_UNIT, new BigDecimal(rate),
                null, minimum == null ? null : new BigDecimal(minimum), new BigDecimal("50"), new BigDecimal("75"));
    }

    static SurgicalComponentRule.Definition byRange(SurgicalComponent component, SurgicalRange... ranges) {
        return new SurgicalComponentRule.Definition(component, SurgicalComponentRule.Mode.BY_RANGE, null,
                List.of(ranges), null, new BigDecimal("50"), new BigDecimal("75"));
    }

    static SurgicalRange range(String from, String to, String value) {
        return new SurgicalRange(new BigDecimal(from), new BigDecimal(to), new BigDecimal(value));
    }

    private static String checksum() {
        return "0".repeat(64);
    }
}

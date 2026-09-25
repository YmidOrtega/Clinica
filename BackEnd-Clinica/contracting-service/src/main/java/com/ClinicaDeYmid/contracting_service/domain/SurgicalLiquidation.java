package com.ClinicaDeYmid.contracting_service.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

public final class SurgicalLiquidation {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private SurgicalLiquidation() {
    }

    public record Procedure(int requestIndex, TariffItem item, String route) {
    }

    public record ComponentCharge(SurgicalComponent component, BigDecimal fullValue, BigDecimal percent,
                                  BigDecimal amount) {
    }

    public record Liquidated(int requestIndex, TariffItem item, String route, int order, boolean principal,
                             boolean sameRoute, List<ComponentCharge> components, BigDecimal total) {
    }

    public static List<Liquidated> liquidate(SurgicalRuleSet rules, BigDecimal factor, List<Procedure> procedures) {
        List<Procedure> ordered = IntStream.range(0, procedures.size()).boxed()
                .sorted(Comparator.<Integer, BigDecimal>comparing(index -> procedures.get(index).item().surgicalBasis())
                        .reversed().thenComparing(Comparator.naturalOrder()))
                .map(procedures::get)
                .toList();
        Set<String> routes = new HashSet<>();
        List<Liquidated> liquidated = new ArrayList<>();
        for (int position = 0; position < ordered.size(); position++) {
            Procedure procedure = ordered.get(position);
            boolean principal = position == 0;
            boolean sameRoute = !principal && routes.contains(procedure.route());
            routes.add(procedure.route());
            List<ComponentCharge> charges = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
            for (SurgicalComponentRule rule : rules.components()) {
                Optional<BigDecimal> value = rule.valueFor(procedure.item().surgicalBasis());
                if (value.isEmpty()) {
                    continue;
                }
                BigDecimal full = procedure.item().manualVersion().inPesos(value.get())
                        .multiply(factor).setScale(2, RoundingMode.HALF_UP);
                BigDecimal percent = rule.percentFor(principal, sameRoute);
                BigDecimal amount = full.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                charges.add(new ComponentCharge(rule.component(), full, percent, amount));
                total = total.add(amount);
            }
            liquidated.add(new Liquidated(procedure.requestIndex(), procedure.item(), procedure.route(), position + 1,
                    principal, sameRoute, charges, total));
        }
        return liquidated;
    }
}

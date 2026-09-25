package com.ClinicaDeYmid.contracting_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "surgical_component_rules")
public class SurgicalComponentRule {

    public enum Mode {
        PER_UNIT,
        BY_RANGE
    }

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_set_id", nullable = false, updatable = false)
    private SurgicalRuleSet ruleSet;

    @Enumerated(EnumType.STRING)
    @Column(name = "component", nullable = false, updatable = false, length = 20)
    private SurgicalComponent component;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, updatable = false, length = 20)
    private Mode mode;

    @Column(name = "rate", updatable = false, precision = 15, scale = 4)
    private BigDecimal rate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ranges", updatable = false)
    private List<SurgicalRange> ranges;

    @Column(name = "minimum_basis", updatable = false, precision = 9, scale = 2)
    private BigDecimal minimumBasis;

    @Column(name = "same_route_percent", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal sameRoutePercent;

    @Column(name = "different_route_percent", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal differentRoutePercent;

    protected SurgicalComponentRule() {
    }

    static SurgicalComponentRule of(SurgicalRuleSet ruleSet, Definition definition) {
        SurgicalComponentRule rule = new SurgicalComponentRule();
        rule.ruleSet = ruleSet;
        rule.component = DomainRules.required(definition.component(), "component");
        rule.mode = DomainRules.required(definition.mode(), "mode");
        rule.minimumBasis = definition.minimumBasis();
        if (rule.minimumBasis != null && rule.minimumBasis.signum() < 0) {
            throw new ContractingException.InvalidData("minimumBasis", "no puede ser negativa");
        }
        rule.sameRoutePercent = percent(definition.sameRoutePercent(), "sameRoutePercent");
        rule.differentRoutePercent = percent(definition.differentRoutePercent(), "differentRoutePercent");
        switch (rule.mode) {
            case PER_UNIT -> {
                rule.rate = DomainRules.required(definition.rate(), "rate");
                if (rule.rate.signum() < 0) {
                    throw new ContractingException.InvalidData("rate", "no puede ser negativa");
                }
                if (definition.ranges() != null && !definition.ranges().isEmpty()) {
                    throw new ContractingException.InvalidData("ranges", "una regla por unidad no lleva rangos");
                }
            }
            case BY_RANGE -> {
                if (definition.rate() != null) {
                    throw new ContractingException.InvalidData("rate", "una regla por rangos no lleva tasa");
                }
                rule.ranges = contiguous(definition.ranges(), rule.component);
            }
        }
        return rule;
    }

    public Optional<BigDecimal> valueFor(BigDecimal basis) {
        if (minimumBasis != null && basis.compareTo(minimumBasis) < 0) {
            return Optional.empty();
        }
        return switch (mode) {
            case PER_UNIT -> Optional.of(rate.multiply(basis));
            case BY_RANGE -> Optional.of(ranges.stream().filter(range -> range.includes(basis)).findFirst()
                    .map(SurgicalRange::value)
                    .orElseThrow(() -> new ContractingException.SurgicalRulesDoNotCover(component, basis)));
        };
    }

    public BigDecimal percentFor(boolean principal, boolean sameRoute) {
        if (principal) {
            return HUNDRED;
        }
        return sameRoute ? sameRoutePercent : differentRoutePercent;
    }

    private static List<SurgicalRange> contiguous(List<SurgicalRange> ranges, SurgicalComponent component) {
        if (ranges == null || ranges.isEmpty()) {
            throw new ContractingException.InvalidData("ranges", "la regla por rangos de " + component + " no trae rangos");
        }
        List<SurgicalRange> sorted = ranges.stream().sorted(Comparator.comparing(SurgicalRange::from)).toList();
        for (int index = 1; index < sorted.size(); index++) {
            if (sorted.get(index).from().compareTo(sorted.get(index - 1).to()) <= 0) {
                throw new ContractingException.InvalidData("ranges", "los rangos de " + component + " se cruzan");
            }
        }
        return sorted;
    }

    private static BigDecimal percent(BigDecimal value, String field) {
        DomainRules.required(value, field);
        if (value.signum() < 0 || value.compareTo(HUNDRED) > 0) {
            throw new ContractingException.InvalidData(field, "debe estar entre 0 y 100");
        }
        return value;
    }

    public SurgicalComponent component() {
        return component;
    }

    public Mode mode() {
        return mode;
    }

    public BigDecimal rate() {
        return rate;
    }

    public List<SurgicalRange> ranges() {
        return ranges == null ? List.of() : List.copyOf(ranges);
    }

    public BigDecimal minimumBasis() {
        return minimumBasis;
    }

    public BigDecimal sameRoutePercent() {
        return sameRoutePercent;
    }

    public BigDecimal differentRoutePercent() {
        return differentRoutePercent;
    }

    public record Definition(SurgicalComponent component, Mode mode, BigDecimal rate, List<SurgicalRange> ranges,
                             BigDecimal minimumBasis, BigDecimal sameRoutePercent, BigDecimal differentRoutePercent) {
    }
}

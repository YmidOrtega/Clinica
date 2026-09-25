package com.ClinicaDeYmid.contracting_service.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "surgical_rule_sets")
public class SurgicalRuleSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, unique = true, length = 36)
    private UUID uuid;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manual_version_id", nullable = false, updatable = false, unique = true)
    private TariffManualVersion manualVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "basis", nullable = false, updatable = false, length = 20)
    private SurgicalBasis basis;

    @Column(name = "checksum", nullable = false, updatable = false, length = 64)
    private String checksum;

    @OneToMany(mappedBy = "ruleSet", cascade = CascadeType.PERSIST)
    @OrderBy("component")
    private List<SurgicalComponentRule> components = new ArrayList<>();

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    @Column(name = "registered_by", updatable = false, length = 36)
    private String registeredBy;

    protected SurgicalRuleSet() {
    }

    public static SurgicalRuleSet load(TariffManualVersion version, SurgicalBasis basis,
                                       List<SurgicalComponentRule.Definition> definitions, String checksum,
                                       String registeredBy, Clock clock) {
        DomainRules.required(version, "manualVersion").requireEditable();
        SurgicalRuleSet rules = new SurgicalRuleSet();
        rules.uuid = UUID.randomUUID();
        rules.manualVersion = version;
        rules.basis = DomainRules.required(basis, "basis");
        rules.checksum = DomainRules.requiredText(checksum, "checksum", 64);
        if (definitions == null || definitions.isEmpty()) {
            throw new ContractingException.InvalidData("components", "debe traer al menos la regla del cirujano");
        }
        Set<SurgicalComponent> seen = EnumSet.noneOf(SurgicalComponent.class);
        for (SurgicalComponentRule.Definition definition : definitions) {
            if (!seen.add(DomainRules.required(definition.component(), "component"))) {
                throw new ContractingException.InvalidData("components",
                        "el componente " + definition.component() + " aparece dos veces");
            }
            rules.components.add(SurgicalComponentRule.of(rules, definition));
        }
        if (!seen.contains(SurgicalComponent.SURGEON)) {
            throw new ContractingException.InvalidData("components", "debe traer la regla del cirujano");
        }
        rules.registeredAt = Instant.now(clock);
        rules.registeredBy = registeredBy;
        return rules;
    }

    public Optional<SurgicalComponentRule> rule(SurgicalComponent component) {
        return components.stream().filter(rule -> rule.component() == component).findFirst();
    }

    public UUID uuid() {
        return uuid;
    }

    public TariffManualVersion manualVersion() {
        return manualVersion;
    }

    public SurgicalBasis basis() {
        return basis;
    }

    public String checksum() {
        return checksum;
    }

    public List<SurgicalComponentRule> components() {
        return List.copyOf(components);
    }

    public Instant registeredAt() {
        return registeredAt;
    }
}

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
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "funding_agreements")
public class FundingAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, unique = true, length = 36)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false, updatable = false)
    private Contract contract;

    @Column(name = "per_capita_value", updatable = false, precision = 15, scale = 2)
    private BigDecimal perCapitaValue;

    @Column(name = "budget_ceiling", updatable = false, precision = 18, scale = 2)
    private BigDecimal budgetCeiling;

    @Enumerated(EnumType.STRING)
    @Column(name = "periodicity", nullable = false, updatable = false, length = 20)
    private SettlementPeriodicity periodicity;

    @Column(name = "technical_note", nullable = false, updatable = false, length = 1000)
    private String technicalNote;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;

    @Column(name = "revoked_from")
    private LocalDate revokedFrom;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    @Column(name = "registered_by", updatable = false, length = 36)
    private String registeredBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by", length = 36)
    private String revokedBy;

    protected FundingAgreement() {
    }

    public static FundingAgreement capitation(Contract contract, BigDecimal perCapitaValue,
                                              SettlementPeriodicity periodicity, String technicalNote,
                                              LocalDate validFrom, String registeredBy, Clock clock) {
        FundingAgreement agreement = base(contract, ContractModality.CAPITATION, periodicity, technicalNote,
                validFrom, registeredBy, clock);
        agreement.perCapitaValue = requireAmount(perCapitaValue, "perCapitaValue", 2);
        return agreement;
    }

    public static FundingAgreement globalBudget(Contract contract, BigDecimal budgetCeiling,
                                                SettlementPeriodicity periodicity, String technicalNote,
                                                LocalDate validFrom, String registeredBy, Clock clock) {
        FundingAgreement agreement = base(contract, ContractModality.GLOBAL_BUDGET, periodicity, technicalNote,
                validFrom, registeredBy, clock);
        agreement.budgetCeiling = requireAmount(budgetCeiling, "budgetCeiling", 2);
        return agreement;
    }

    public void revoke(LocalDate from, String actor, Clock clock) {
        if (revokedFrom != null) {
            throw new ContractingException.FundingAgreementAlreadyRevoked();
        }
        LocalDate end = DomainRules.required(from, "revokedFrom");
        if (end.isBefore(validFrom)) {
            throw new ContractingException.InvalidData("revokedFrom", "no puede ser anterior al inicio del acuerdo");
        }
        revokedFrom = end;
        revokedAt = Instant.now(clock);
        revokedBy = actor;
    }

    public boolean appliesOn(LocalDate date) {
        return !date.isBefore(validFrom) && (revokedFrom == null || date.isBefore(revokedFrom));
    }

    public UUID uuid() {
        return uuid;
    }

    public Contract contract() {
        return contract;
    }

    public ContractModality modality() {
        return contract.modality();
    }

    public BigDecimal perCapitaValue() {
        return perCapitaValue;
    }

    public BigDecimal budgetCeiling() {
        return budgetCeiling;
    }

    public SettlementPeriodicity periodicity() {
        return periodicity;
    }

    public String technicalNote() {
        return technicalNote;
    }

    public LocalDate validFrom() {
        return validFrom;
    }

    public LocalDate revokedFrom() {
        return revokedFrom;
    }

    public Instant registeredAt() {
        return registeredAt;
    }

    public String registeredBy() {
        return registeredBy;
    }

    private static FundingAgreement base(Contract contract, ContractModality expected, SettlementPeriodicity periodicity,
                                         String technicalNote, LocalDate validFrom, String registeredBy, Clock clock) {
        FundingAgreement agreement = new FundingAgreement();
        agreement.uuid = UUID.randomUUID();
        agreement.contract = DomainRules.required(contract, "contract");
        if (contract.modality() != expected) {
            throw new ContractingException.FundingNotApplicable(contract.modality(), expected);
        }
        agreement.periodicity = DomainRules.required(periodicity, "periodicity");
        agreement.technicalNote = DomainRules.requiredText(technicalNote, "technicalNote", 1000);
        agreement.validFrom = DomainRules.required(validFrom, "validFrom");
        if (validFrom.isBefore(contract.validFrom())) {
            throw new ContractingException.InvalidData("validFrom", "no puede empezar antes que el contrato");
        }
        agreement.registeredAt = Instant.now(clock);
        agreement.registeredBy = registeredBy;
        return agreement;
    }

    private static BigDecimal requireAmount(BigDecimal amount, String field, int scale) {
        BigDecimal value = DomainRules.required(amount, field);
        if (value.signum() <= 0) {
            throw new ContractingException.InvalidData(field, "debe ser mayor que cero");
        }
        if (value.stripTrailingZeros().scale() > scale) {
            throw new ContractingException.InvalidData(field, "no puede tener más de " + scale + " decimales");
        }
        return value.setScale(scale, RoundingMode.HALF_UP);
    }
}

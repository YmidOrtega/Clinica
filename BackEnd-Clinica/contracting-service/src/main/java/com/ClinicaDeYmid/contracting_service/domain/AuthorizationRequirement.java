package com.ClinicaDeYmid.contracting_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "contract_authorization_requirements")
public class AuthorizationRequirement {

    private static final Pattern CUPS = Pattern.compile("^[0-9]{6,8}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, unique = true, length = 36)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false, updatable = false)
    private Contract contract;

    @Column(name = "cups_code", nullable = false, updatable = false, length = 8)
    private String cupsCode;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;

    @Column(name = "revoked_from")
    private LocalDate revokedFrom;

    @Column(name = "revocation_reason", length = 500)
    private String revocationReason;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    @Column(name = "registered_by", updatable = false, length = 36)
    private String registeredBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by", length = 36)
    private String revokedBy;

    protected AuthorizationRequirement() {
    }

    public static AuthorizationRequirement register(Contract contract, String cupsCode, LocalDate validFrom,
                                                    String registeredBy, Clock clock) {
        AuthorizationRequirement requirement = new AuthorizationRequirement();
        requirement.uuid = UUID.randomUUID();
        requirement.contract = DomainRules.required(contract, "contract");
        requirement.cupsCode = DomainRules.matching(DomainRules.requiredText(cupsCode, "cupsCode", 8), CUPS, "cupsCode");
        requirement.validFrom = DomainRules.required(validFrom, "validFrom");
        if (validFrom.isBefore(contract.validFrom())) {
            throw new ContractingException.InvalidData("validFrom", "no puede empezar antes que el contrato");
        }
        requirement.registeredAt = Instant.now(clock);
        requirement.registeredBy = registeredBy;
        return requirement;
    }

    public void revoke(LocalDate from, String motive, String actor, Clock clock) {
        if (revokedFrom != null) {
            throw new ContractingException.RequirementAlreadyRevoked();
        }
        LocalDate end = DomainRules.required(from, "revokedFrom");
        if (end.isBefore(validFrom)) {
            throw new ContractingException.InvalidData("revokedFrom", "no puede ser anterior al inicio del requisito");
        }
        revokedFrom = end;
        revocationReason = DomainRules.requiredText(motive, "revocationReason", 500);
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

    public String cupsCode() {
        return cupsCode;
    }

    public LocalDate validFrom() {
        return validFrom;
    }

    public LocalDate revokedFrom() {
        return revokedFrom;
    }

    public String revocationReason() {
        return revocationReason;
    }

    public Instant registeredAt() {
        return registeredAt;
    }

    public String registeredBy() {
        return registeredBy;
    }
}

package com.ClinicaDeYmid.contracting_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "contracts")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class Contract {

    private static final Pattern NUMBER = Pattern.compile("^[A-Z0-9][A-Z0-9./-]{1,39}$");
    private static final BigDecimal MINIMUM_FACTOR = new BigDecimal("0.1000");
    private static final BigDecimal MAXIMUM_FACTOR = new BigDecimal("10.0000");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, unique = true, length = 36)
    private UUID uuid;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false, updatable = false)
    private Payer payer;

    @Column(name = "number", nullable = false, updatable = false, length = 40)
    private String number;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "modality", nullable = false, updatable = false, length = 20)
    private ContractModality modality;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tariff_version_id")
    private TariffManualVersion tariffVersion;

    @Column(name = "tariff_factor", precision = 6, scale = 4)
    private BigDecimal tariffFactor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ContractStatus.Code statusCode;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 36)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 36)
    private String updatedBy;

    protected Contract() {
    }

    public static Contract draft(Payer payer, String number, String name, ContractModality modality,
                                 LocalDate validFrom, LocalDate validTo) {
        Contract contract = new Contract();
        contract.uuid = UUID.randomUUID();
        contract.payer = DomainRules.required(payer, "payer");
        if (!payer.status().contractable()) {
            throw new ContractingException.PayerNotActive();
        }
        contract.number = DomainRules.matching(
                DomainRules.upper(DomainRules.requiredText(number, "number", 40)), NUMBER, "number");
        contract.name = DomainRules.requiredText(name, "name", 200);
        contract.modality = DomainRules.required(modality, "modality");
        contract.applyValidity(validFrom, validTo);
        contract.statusCode = ContractStatus.Code.DRAFT;
        return contract;
    }

    public void agreeTariff(TariffManualVersion version, BigDecimal factor) {
        requireNegotiable();
        if (!modality.pricedPerService()) {
            throw new ContractingException.TariffNotApplicable(modality);
        }
        TariffManualVersion agreed = DomainRules.required(version, "tariffVersion");
        if (agreed.status().editable()) {
            throw new ContractingException.TariffVersionNotEditable("todavía es un borrador");
        }
        tariffVersion = agreed;
        tariffFactor = requireFactor(factor);
    }

    public void rename(String newName) {
        name = DomainRules.requiredText(newName, "name", 200);
    }

    public void extendTo(LocalDate newValidTo) {
        if (statusCode == ContractStatus.Code.TERMINATED) {
            throw new ContractingException.InvalidContractTransition(statusCode, statusCode);
        }
        applyValidity(validFrom, newValidTo);
    }

    public void activate(Clock clock) {
        if (modality.pricedPerService() && tariffVersion == null) {
            throw new ContractingException.TariffTermsMissing();
        }
        Instant now = Instant.now(clock);
        applyStatus(status().activate(now), now);
    }

    public void suspend(String reason, Clock clock) {
        Instant now = Instant.now(clock);
        applyStatus(status().suspend(reason, now), now);
    }

    public void terminate(String reason, Clock clock) {
        Instant now = Instant.now(clock);
        applyStatus(status().terminate(reason, now), now);
    }

    public boolean inForceOn(LocalDate date) {
        return status().billable() && !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
    }

    public void requireInForceOn(LocalDate date) {
        if (!inForceOn(date)) {
            throw new ContractingException.ContractNotInForce(date);
        }
    }

    public void requireNegotiable() {
        if (!status().negotiable()) {
            throw new ContractingException.ContractNotNegotiable();
        }
    }

    public ContractStatus status() {
        return switch (statusCode) {
            case DRAFT -> new ContractStatus.Draft();
            case ACTIVE -> new ContractStatus.Active(statusChangedAt);
            case SUSPENDED -> new ContractStatus.Suspended(statusReason, statusChangedAt);
            case TERMINATED -> new ContractStatus.Terminated(statusReason, statusChangedAt);
        };
    }

    public UUID uuid() {
        return uuid;
    }

    public long version() {
        return version;
    }

    public Payer payer() {
        return payer;
    }

    public String number() {
        return number;
    }

    public String name() {
        return name;
    }

    public ContractModality modality() {
        return modality;
    }

    public LocalDate validFrom() {
        return validFrom;
    }

    public LocalDate validTo() {
        return validTo;
    }

    public TariffManualVersion tariffVersion() {
        return tariffVersion;
    }

    public BigDecimal tariffFactor() {
        return tariffFactor;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private void applyValidity(LocalDate from, LocalDate to) {
        LocalDate start = DomainRules.required(from, "validFrom");
        if (to != null && !to.isAfter(start)) {
            throw new ContractingException.InvalidData("validTo", "debe ser posterior al inicio de la vigencia");
        }
        validFrom = start;
        validTo = to;
    }

    private void applyStatus(ContractStatus status, Instant changedAt) {
        statusCode = status.code();
        statusReason = switch (status) {
            case ContractStatus.Draft draft -> null;
            case ContractStatus.Active active -> null;
            case ContractStatus.Suspended suspended -> suspended.reason();
            case ContractStatus.Terminated terminated -> terminated.reason();
        };
        statusChangedAt = changedAt;
    }

    private static BigDecimal requireFactor(BigDecimal factor) {
        BigDecimal value = DomainRules.required(factor, "tariffFactor");
        if (value.compareTo(MINIMUM_FACTOR) < 0 || value.compareTo(MAXIMUM_FACTOR) > 0) {
            throw new ContractingException.InvalidData("tariffFactor", "debe estar entre 0.1 y 10");
        }
        if (value.stripTrailingZeros().scale() > 4) {
            throw new ContractingException.InvalidData("tariffFactor", "no puede tener más de cuatro decimales");
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}

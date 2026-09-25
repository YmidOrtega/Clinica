package com.ClinicaDeYmid.billing_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "numbering_resolutions")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class NumberingResolution {

    static final int EXPIRY_WARNING_DAYS = 30;
    static final int RUNNING_OUT_PERCENT = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, unique = true, length = 36)
    private UUID uuid;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "resolution_number", nullable = false, updatable = false, length = 20)
    private String resolutionNumber;

    @Column(name = "issued_on", nullable = false, updatable = false)
    private LocalDate issuedOn;

    @Column(name = "prefix", nullable = false, updatable = false, length = 4)
    private String prefix;

    @Column(name = "range_from", nullable = false, updatable = false)
    private long rangeFrom;

    @Column(name = "range_to", nullable = false, updatable = false)
    private long rangeTo;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false, updatable = false)
    private LocalDate validUntil;

    @Column(name = "technical_key", nullable = false, updatable = false, length = 100)
    private String technicalKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, updatable = false, length = 20)
    private DianEnvironment environment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ResolutionStatus.Code statusCode;

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

    protected NumberingResolution() {
    }

    public static NumberingResolution register(ResolutionTerms terms, DianEnvironment environment) {
        DomainRules.required(terms, "terms");
        NumberingResolution resolution = new NumberingResolution();
        resolution.uuid = UUID.randomUUID();
        resolution.resolutionNumber = terms.resolutionNumber();
        resolution.issuedOn = terms.issuedOn();
        resolution.prefix = terms.prefix();
        resolution.rangeFrom = terms.rangeFrom();
        resolution.rangeTo = terms.rangeTo();
        resolution.validFrom = terms.validFrom();
        resolution.validUntil = terms.validUntil();
        resolution.technicalKey = terms.technicalKey();
        resolution.environment = DomainRules.required(environment, "environment");
        resolution.statusCode = ResolutionStatus.Code.PENDING;
        return resolution;
    }

    public void activate(DianEnvironment issuerEnvironment, Clock clock) {
        if (environment != issuerEnvironment) {
            throw new BillingException.EnvironmentMismatch();
        }
        if (!validOn(LocalDate.now(clock))) {
            throw new BillingException.OutsideValidity();
        }
        applyStatus(status().activate(Instant.now(clock)));
    }

    public void retire(String reason, Clock clock) {
        applyStatus(status().retire(reason, Instant.now(clock)));
    }

    public IssuedNumber issue(long consecutive, Clock clock) {
        if (!(status() instanceof ResolutionStatus.Active)) {
            throw new BillingException.NoActiveResolution();
        }
        if (!validOn(LocalDate.now(clock))) {
            throw new BillingException.OutsideValidity();
        }
        if (consecutive < rangeFrom || consecutive > rangeTo) {
            throw new BillingException.ResolutionExhausted();
        }
        IssuedNumber issued = new IssuedNumber(uuid, prefix, consecutive);
        if (consecutive == rangeTo) {
            applyStatus(status().exhaust(Instant.now(clock)));
        }
        return issued;
    }

    public long remaining(long nextNumber) {
        return Math.max(0, rangeTo - Math.max(nextNumber, rangeFrom) + 1);
    }

    public Set<ResolutionAlert> alerts(long nextNumber, Clock clock) {
        Set<ResolutionAlert> alerts = EnumSet.noneOf(ResolutionAlert.class);
        ResolutionStatus.Code code = statusCode;
        if (code != ResolutionStatus.Code.PENDING && code != ResolutionStatus.Code.ACTIVE) {
            return alerts;
        }
        LocalDate today = LocalDate.now(clock);
        if (today.isAfter(validUntil)) {
            alerts.add(ResolutionAlert.EXPIRED);
        } else if (!today.plusDays(EXPIRY_WARNING_DAYS).isBefore(validUntil)) {
            alerts.add(ResolutionAlert.EXPIRES_SOON);
        }
        long size = rangeTo - rangeFrom + 1;
        if (code == ResolutionStatus.Code.ACTIVE && remaining(nextNumber) * 100 <= size * RUNNING_OUT_PERCENT) {
            alerts.add(ResolutionAlert.RUNNING_OUT);
        }
        return alerts;
    }

    public boolean validOn(LocalDate day) {
        return !day.isBefore(validFrom) && !day.isAfter(validUntil);
    }

    public ResolutionStatus status() {
        return switch (statusCode) {
            case PENDING -> new ResolutionStatus.Pending();
            case ACTIVE -> new ResolutionStatus.Active(statusChangedAt);
            case EXHAUSTED -> new ResolutionStatus.Exhausted(statusChangedAt);
            case RETIRED -> new ResolutionStatus.Retired(statusReason, statusChangedAt);
        };
    }

    private void applyStatus(ResolutionStatus status) {
        this.statusCode = status.code();
        switch (status) {
            case ResolutionStatus.Pending ignored -> {
                this.statusReason = null;
                this.statusChangedAt = null;
            }
            case ResolutionStatus.Active active -> {
                this.statusReason = null;
                this.statusChangedAt = active.since();
            }
            case ResolutionStatus.Exhausted exhausted -> {
                this.statusReason = null;
                this.statusChangedAt = exhausted.since();
            }
            case ResolutionStatus.Retired retired -> {
                this.statusReason = retired.reason();
                this.statusChangedAt = retired.since();
            }
        }
    }

    public ResolutionTerms terms() {
        return new ResolutionTerms(resolutionNumber, issuedOn, prefix, rangeFrom, rangeTo, validFrom, validUntil,
                technicalKey);
    }

    public UUID uuid() {
        return uuid;
    }

    public long version() {
        return version;
    }

    public DianEnvironment environment() {
        return environment;
    }

    public Instant registeredAt() {
        return createdAt;
    }
}

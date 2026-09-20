package com.ClinicaDeYmid.admissions_service.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import org.hibernate.envers.AuditJoinTable;
import org.hibernate.envers.AuditTable;
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "authorizations")
@Audited
@AuditTable(value = "authorizations_aud", schema = "admissions_history")
@EntityListeners(AuditingEntityListener.class)
public class Authorization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false, unique = true)
    private UUID uuid;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admission_id", nullable = false, updatable = false)
    private Admission admission;

    @Column(name = "number", nullable = false, updatable = false, length = 60)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private AuthorizationType type;

    @Column(name = "authorized_by", length = 160)
    private String authorizedBy;

    @Column(name = "copayment", precision = 15, scale = 2)
    private BigDecimal copayment;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "authorization_portfolio_items", schema = "admissions",
            joinColumns = @JoinColumn(name = "authorization_id"))
    @Column(name = "portfolio_item_uuid", nullable = false)
    @AuditJoinTable(name = "authorization_portfolio_items_aud", schema = "admissions_history")
    private Set<UUID> authorizedItems = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AuthorizationStatus.Code statusCode;

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

    protected Authorization() {
    }

    public static Authorization grant(Admission admission, String number, AuthorizationType type, String authorizedBy,
                                      BigDecimal copayment, LocalDate validFrom, LocalDate validTo,
                                      Set<UUID> authorizedItems) {
        Authorization authorization = new Authorization();
        authorization.uuid = UUID.randomUUID();
        authorization.admission = DomainRules.required(admission, "admission");
        if (!admission.status().open()) {
            throw new AdmissionsException.ClosedAdmission();
        }
        authorization.number = DomainRules.requiredText(number, "number", 60);
        authorization.type = DomainRules.required(type, "type");
        authorization.authorizedBy = DomainRules.optionalText(authorizedBy, "authorizedBy", 160);
        authorization.copayment = validCopayment(copayment);
        authorization.validity(validFrom, validTo);
        authorization.authorizedItems = authorizedItems == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(authorizedItems);
        authorization.statusCode = AuthorizationStatus.Code.ACTIVE;
        return authorization;
    }

    public void revoke(String reason, Clock clock) {
        applyStatus(status().revoke(reason, Instant.now(clock)));
    }

    public boolean covers(UUID portfolioItem, LocalDate on) {
        return status().usable() && inForceOn(on)
                && (authorizedItems.isEmpty() || authorizedItems.contains(portfolioItem));
    }

    public boolean inForceOn(LocalDate date) {
        return (validFrom == null || !date.isBefore(validFrom)) && (validTo == null || !date.isAfter(validTo));
    }

    public AuthorizationStatus status() {
        return switch (statusCode) {
            case ACTIVE -> new AuthorizationStatus.Active();
            case REVOKED -> new AuthorizationStatus.Revoked(statusReason, statusChangedAt);
        };
    }

    private void applyStatus(AuthorizationStatus status) {
        this.statusCode = status.code();
        switch (status) {
            case AuthorizationStatus.Active ignored -> {
                this.statusReason = null;
                this.statusChangedAt = null;
            }
            case AuthorizationStatus.Revoked revoked -> {
                this.statusReason = revoked.reason();
                this.statusChangedAt = revoked.at();
            }
        }
    }

    private void validity(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new AdmissionsException.InvalidData("validTo", "no puede ser anterior a validFrom");
        }
        this.validFrom = from;
        this.validTo = to;
    }

    private static BigDecimal validCopayment(BigDecimal copayment) {
        if (copayment == null) {
            return null;
        }
        if (copayment.signum() < 0) {
            throw new AdmissionsException.InvalidData("copayment", "no puede ser negativo");
        }
        return copayment.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public UUID uuid() {
        return uuid;
    }

    public long version() {
        return version;
    }

    public String number() {
        return number;
    }

    public AuthorizationType type() {
        return type;
    }

    public String authorizedBy() {
        return authorizedBy;
    }

    public BigDecimal copayment() {
        return copayment;
    }

    public LocalDate validFrom() {
        return validFrom;
    }

    public LocalDate validTo() {
        return validTo;
    }

    public Set<UUID> authorizedItems() {
        return Set.copyOf(authorizedItems);
    }

    public Admission admission() {
        return admission;
    }
}

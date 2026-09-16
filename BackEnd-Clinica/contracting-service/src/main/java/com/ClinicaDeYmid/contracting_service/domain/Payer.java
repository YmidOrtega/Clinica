package com.ClinicaDeYmid.contracting_service.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
import java.util.UUID;

@Entity
@Table(name = "payers")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class Payer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, unique = true, length = 36)
    private UUID uuid;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "social_reason", nullable = false, length = 200)
    private String socialReason;

    @Embedded
    @AttributeOverride(name = "number", column = @Column(name = "nit", nullable = false, length = 10))
    @AttributeOverride(name = "verificationDigit", column = @Column(name = "nit_verification_digit", nullable = false))
    private Nit nit;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private PayerType type;

    @Column(name = "adres_code", length = 20)
    private String adresCode;

    @Embedded
    @AttributeOverride(name = "address", column = @Column(name = "address", nullable = false, length = 255))
    @AttributeOverride(name = "phone", column = @Column(name = "phone", nullable = false, length = 16))
    @AttributeOverride(name = "billingEmail", column = @Column(name = "billing_email", length = 150))
    private ContactInfo contact;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayerStatus.Code statusCode;

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

    protected Payer() {
    }

    public static Payer register(PayerRegistration registration, Clock clock) {
        DomainRules.required(registration, "registration");
        Payer payer = new Payer();
        payer.uuid = UUID.randomUUID();
        payer.applyIdentity(new PayerIdentity(registration.socialReason(), registration.nit(),
                registration.type(), registration.adresCode()));
        payer.contact = registration.contact();
        payer.applyStatus(new PayerStatus.Active(), Instant.now(clock));
        return payer;
    }

    public void correctIdentity(PayerIdentity identity) {
        requireNotDeactivated();
        applyIdentity(DomainRules.required(identity, "identity"));
    }

    public void updateContact(ContactInfo newContact) {
        requireNotDeactivated();
        contact = DomainRules.required(newContact, "contact");
    }

    public void suspend(String reason, Clock clock) {
        Instant now = Instant.now(clock);
        applyStatus(status().suspend(reason, now), now);
    }

    public void reactivate(Clock clock) {
        Instant now = Instant.now(clock);
        applyStatus(status().reactivate(now), now);
    }

    public void deactivate(String reason, Clock clock) {
        Instant now = Instant.now(clock);
        applyStatus(status().deactivate(reason, now), now);
    }

    public PayerStatus status() {
        return switch (statusCode) {
            case ACTIVE -> new PayerStatus.Active();
            case SUSPENDED -> new PayerStatus.Suspended(statusReason, statusChangedAt);
            case DEACTIVATED -> new PayerStatus.Deactivated(statusReason, statusChangedAt);
        };
    }

    public PayerIdentity identity() {
        return new PayerIdentity(socialReason, nit, type, adresCode);
    }

    public UUID uuid() {
        return uuid;
    }

    public long version() {
        return version;
    }

    public String socialReason() {
        return socialReason;
    }

    public Nit nit() {
        return nit;
    }

    public PayerType type() {
        return type;
    }

    public String adresCode() {
        return adresCode;
    }

    public ContactInfo contact() {
        return contact;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private void applyIdentity(PayerIdentity identity) {
        socialReason = identity.socialReason();
        nit = identity.nit();
        type = identity.type();
        adresCode = identity.adresCode();
    }

    private void applyStatus(PayerStatus status, Instant changedAt) {
        statusCode = status.code();
        statusReason = switch (status) {
            case PayerStatus.Active active -> null;
            case PayerStatus.Suspended suspended -> suspended.reason();
            case PayerStatus.Deactivated deactivated -> deactivated.reason();
        };
        statusChangedAt = status instanceof PayerStatus.Active ? null : changedAt;
    }

    private void requireNotDeactivated() {
        if (statusCode == PayerStatus.Code.DEACTIVATED) {
            throw new ContractingException.PayerNotActive();
        }
    }
}

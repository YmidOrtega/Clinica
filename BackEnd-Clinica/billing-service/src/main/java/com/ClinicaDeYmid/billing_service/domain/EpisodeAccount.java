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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "episode_accounts")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class EpisodeAccount {

    static final String CANCELLED_WITHOUT_REASON = "Admisión anulada en admisiones";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, unique = true, length = 36)
    private UUID uuid;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "admission_uuid", nullable = false, updatable = false, unique = true, length = 36)
    private UUID admissionUuid;

    @Column(name = "admission_number", nullable = false, updatable = false, unique = true, length = 15)
    private String admissionNumber;

    @Column(name = "admission_version", nullable = false)
    private long admissionVersion;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "patient_uuid", nullable = false, length = 36)
    private UUID patientUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "admission_kind", nullable = false, length = 20)
    private AdmissionKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "admission_status", nullable = false, length = 20)
    private AdmissionSnapshot.Status admissionStatus;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "configuration_service_uuid", nullable = false, length = 36)
    private UUID configurationServiceUuid;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus.Code statusCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "discharge", length = 20)
    private DischargeType discharge;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EpisodeAccount() {
    }

    public static EpisodeAccount open(AdmissionSnapshot snapshot) {
        DomainRules.required(snapshot, "snapshot");
        EpisodeAccount account = new EpisodeAccount();
        account.uuid = UUID.randomUUID();
        account.admissionUuid = snapshot.admissionUuid();
        account.admissionNumber = snapshot.admissionNumber();
        account.openedAt = snapshot.occurredAt();
        account.statusCode = AccountStatus.Code.OPEN;
        account.admissionVersion = snapshot.admissionVersion();
        account.follow(snapshot);
        return account;
    }

    public boolean follow(AdmissionSnapshot snapshot) {
        if (!snapshot.admissionUuid().equals(admissionUuid)) {
            throw new IllegalArgumentException("The snapshot belongs to another admission");
        }
        if (snapshot.admissionVersion() < admissionVersion || statusCode != AccountStatus.Code.OPEN) {
            return false;
        }
        admissionVersion = snapshot.admissionVersion();
        patientUuid = snapshot.patientUuid();
        kind = snapshot.kind();
        admissionStatus = snapshot.status();
        configurationServiceUuid = snapshot.configurationServiceUuid();
        switch (snapshot.status()) {
            case DISCHARGED -> applyStatus(new AccountStatus.Frozen(snapshot.discharge(), snapshot.occurredAt()));
            case CANCELLED -> applyStatus(new AccountStatus.Voided(
                    snapshot.reason() == null ? CANCELLED_WITHOUT_REASON : snapshot.reason(), snapshot.occurredAt()));
            case REGISTERED, ACTIVE -> {
            }
        }
        return true;
    }

    public AccountStatus status() {
        return switch (statusCode) {
            case OPEN -> new AccountStatus.Open();
            case FROZEN -> new AccountStatus.Frozen(discharge, statusChangedAt);
            case VOIDED -> new AccountStatus.Voided(statusReason, statusChangedAt);
        };
    }

    private void applyStatus(AccountStatus status) {
        statusCode = status.code();
        switch (status) {
            case AccountStatus.Open ignored -> {
                discharge = null;
                statusReason = null;
                statusChangedAt = null;
            }
            case AccountStatus.Frozen frozen -> {
                discharge = frozen.discharge();
                statusReason = null;
                statusChangedAt = frozen.since();
            }
            case AccountStatus.Voided voided -> {
                discharge = null;
                statusReason = voided.reason();
                statusChangedAt = voided.since();
            }
        }
    }

    public UUID uuid() {
        return uuid;
    }

    public long version() {
        return version;
    }

    public UUID admissionUuid() {
        return admissionUuid;
    }

    public String admissionNumber() {
        return admissionNumber;
    }

    public long admissionVersion() {
        return admissionVersion;
    }

    public UUID patientUuid() {
        return patientUuid;
    }

    public AdmissionKind kind() {
        return kind;
    }

    public AdmissionSnapshot.Status admissionStatus() {
        return admissionStatus;
    }

    public UUID configurationServiceUuid() {
        return configurationServiceUuid;
    }

    public Instant openedAt() {
        return openedAt;
    }
}

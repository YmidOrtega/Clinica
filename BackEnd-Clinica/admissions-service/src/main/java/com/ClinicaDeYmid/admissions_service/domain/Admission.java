package com.ClinicaDeYmid.admissions_service.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.envers.AuditTable;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "admissions")
@Audited
@AuditTable(value = "admissions_aud", schema = "admissions_history")
@EntityListeners(AuditingEntityListener.class)
public class Admission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false, unique = true)
    private UUID uuid;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "number", nullable = false, updatable = false, unique = true, length = 20)
    private String number;

    @Column(name = "patient_uuid", nullable = false, updatable = false)
    private UUID patientUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "cause", nullable = false, length = 30)
    private Cause cause;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "care_type_id")
    private CareType careType;

    @Embedded
    private Companion companion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AdmissionStatus.Code statusCode;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @NotAudited
    @OneToMany(mappedBy = "admission", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("startedAt asc, id asc")
    private List<AdmissionPhase> phases = new ArrayList<>();

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

    protected Admission() {
    }

    public static Admission register(String number, UUID patientUuid, ConfigurationService service,
                                     Cause cause, CareType careType, Companion companion, Clock clock) {
        Admission admission = new Admission();
        admission.uuid = UUID.randomUUID();
        admission.number = DomainRules.requiredText(number, "number", 20);
        admission.patientUuid = DomainRules.required(patientUuid, "patientUuid");
        admission.cause = DomainRules.required(cause, "cause");
        admission.companion = companion;
        admission.statusCode = AdmissionStatus.Code.REGISTERED;
        admission.assignCareType(careType, DomainRules.required(service, "configurationService"));
        admission.phases.add(AdmissionPhase.begin(admission, service, Instant.now(clock), null));
        return admission;
    }

    public void activate(Clock clock) {
        applyStatus(status().activate(Instant.now(clock)));
    }

    public void discharge(Clock clock) {
        applyStatus(status().discharge(Instant.now(clock)));
        currentPhase().end(Instant.now(clock));
    }

    public void cancel(String reason, Clock clock) {
        Instant now = Instant.now(clock);
        applyStatus(status().cancel(reason, now));
        currentPhase().end(now);
    }

    public AdmissionPhase moveTo(ConfigurationService service, String reason, Clock clock) {
        if (!status().open()) {
            throw new AdmissionsException.InvalidAdmissionTransition(statusCode, AdmissionStatus.Code.ACTIVE);
        }
        AdmissionPhase current = currentPhase();
        if (current.configurationService().uuid().equals(service.uuid())) {
            throw new AdmissionsException.SamePhaseAlreadyCurrent();
        }
        Instant now = Instant.now(clock);
        current.end(now);
        AdmissionPhase next = AdmissionPhase.begin(this, service, now, reason);
        phases.add(next);
        return next;
    }

    public void accompaniedBy(Companion companion) {
        if (!status().editable()) {
            throw new AdmissionsException.InvalidAdmissionTransition(statusCode, statusCode);
        }
        this.companion = companion;
    }

    public AdmissionPhase currentPhase() {
        return phases.stream()
                .filter(AdmissionPhase::current)
                .reduce((first, second) -> second)
                .orElseThrow(AdmissionsException.PhaseAlreadyClosed::new);
    }

    public AdmissionKind kind() {
        return currentPhase().kind();
    }

    public boolean bedRequired() {
        return currentPhase().bedRequired();
    }

    public boolean coverageMayBlockAdmission() {
        return currentPhase().coverageMayBlockAdmission();
    }

    public AdmissionStatus status() {
        return switch (statusCode) {
            case REGISTERED -> new AdmissionStatus.Registered();
            case ACTIVE -> new AdmissionStatus.Active(statusChangedAt);
            case DISCHARGED -> new AdmissionStatus.Discharged(statusChangedAt);
            case CANCELLED -> new AdmissionStatus.Cancelled(statusReason, statusChangedAt);
        };
    }

    private void applyStatus(AdmissionStatus status) {
        this.statusCode = status.code();
        switch (status) {
            case AdmissionStatus.Registered ignored -> {
                this.statusReason = null;
                this.statusChangedAt = null;
            }
            case AdmissionStatus.Active active -> {
                this.statusReason = null;
                this.statusChangedAt = active.since();
            }
            case AdmissionStatus.Discharged discharged -> {
                this.statusReason = null;
                this.statusChangedAt = discharged.at();
            }
            case AdmissionStatus.Cancelled cancelled -> {
                this.statusReason = cancelled.reason();
                this.statusChangedAt = cancelled.at();
            }
        }
    }

    private void assignCareType(CareType careType, ConfigurationService service) {
        if (careType == null) {
            return;
        }
        if (!careType.serviceType().uuid().equals(service.serviceType().uuid())) {
            throw new AdmissionsException.CareTypeDoesNotBelongToTheService();
        }
        this.careType = careType;
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

    public UUID patientUuid() {
        return patientUuid;
    }

    public Cause cause() {
        return cause;
    }

    public CareType careType() {
        return careType;
    }

    public Companion companion() {
        return companion;
    }

    public List<AdmissionPhase> phases() {
        return List.copyOf(phases);
    }
}

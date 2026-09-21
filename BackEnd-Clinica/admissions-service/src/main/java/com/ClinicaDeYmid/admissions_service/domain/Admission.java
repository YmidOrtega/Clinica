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
import jakarta.persistence.Transient;
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

    @Embedded
    private Coverage coverage;

    @Column(name = "bed_uuid")
    private UUID bedUuid;

    @Embedded
    private AttendingPractitioner attending;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AdmissionStatus.Code statusCode;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Embedded
    private DischargeDetails discharge;

    @Embedded
    private DeathNotice deathNotice;

    @Embedded
    private Triage triage;

    @NotAudited
    @OneToMany(mappedBy = "admission", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("startedAt asc, id asc")
    private List<AdmissionPhase> phases = new ArrayList<>();

    @Transient
    private final List<AdmissionEvent> events = new ArrayList<>();

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
        admission.events.add(new AdmissionEvent.Registered());
        return admission;
    }

    public void activate(Clock clock) {
        if (bedRequired() && bedUuid == null) {
            throw new AdmissionsException.BedRequired();
        }
        applyStatus(status().activate(Instant.now(clock)));
    }

    public void attendedBy(AttendingPractitioner practitioner) {
        if (!status().open()) {
            throw new AdmissionsException.ClosedAdmission();
        }
        this.attending = DomainRules.required(practitioner, "attending");
    }

    public AttendingPractitioner attending() {
        return attending;
    }

    public void assignBed(UUID bed) {
        if (!status().open()) {
            throw new AdmissionsException.ClosedAdmission();
        }
        UUID previous = bedUuid;
        this.bedUuid = DomainRules.required(bed, "bed");
        events.add(new AdmissionEvent.BedAssigned(bedUuid, previous));
    }

    public UUID releaseBed() {
        if (bedUuid == null) {
            throw new AdmissionsException.NoBedAssigned();
        }
        UUID released = bedUuid;
        this.bedUuid = null;
        events.add(new AdmissionEvent.BedReleased(released));
        return released;
    }

    public boolean occupiesABed() {
        return bedUuid != null;
    }

    public UUID bedUuid() {
        return bedUuid;
    }

    public void discharge(Discharge discharge) {
        applyStatus(status().discharge(discharge));
        currentPhase().end(discharge.at());
        events.add(new AdmissionEvent.Discharged(discharge.code()));
        if (discharge instanceof Discharge.Death) {
            this.deathNotice = DeathNotice.pending(
                    "El fallecimiento todavía no se informó al directorio de pacientes", discharge.at());
        }
    }

    public void deathNoticeSent(Instant at) {
        requireADeath();
        this.deathNotice = DeathNotice.sent(at);
    }

    public void deathNoticeFailed(String detail, Instant at) {
        requireADeath();
        this.deathNotice = DeathNotice.pending(detail, at);
    }

    public Discharge.Death death() {
        if (status() instanceof AdmissionStatus.Discharged discharged
                && discharged.discharge() instanceof Discharge.Death death) {
            return death;
        }
        throw new AdmissionsException.NoDeathToReport();
    }

    public DeathNotice deathNotice() {
        return deathNotice;
    }

    public boolean reflectTriage(Triage.Level level, Instant at, UUID clinician) {
        Triage classified = Triage.classified(level, at, clinician);
        if (!classified.newerThan(triage)) {
            return false;
        }
        this.triage = classified;
        return true;
    }

    public Triage triage() {
        return triage;
    }

    private void requireADeath() {
        death();
    }

    public void cancel(String reason, Clock clock) {
        Instant now = Instant.now(clock);
        applyStatus(status().cancel(reason, now));
        currentPhase().end(now);
        events.add(new AdmissionEvent.Cancelled(statusReason));
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
        UUID previousService = current.configurationService().uuid();
        current.end(now);
        AdmissionPhase next = AdmissionPhase.begin(this, service, now, reason);
        phases.add(next);
        events.add(new AdmissionEvent.PhaseChanged(previousService, next.openingReason()));
        return next;
    }

    public void assess(Coverage coverage) {
        this.coverage = DomainRules.required(coverage, "coverage");
        if (this.coverage.pending()) {
            events.add(new AdmissionEvent.CoveragePending(this.coverage.status(), this.coverage.detail()));
        }
    }

    public List<AdmissionEvent> pullEvents() {
        List<AdmissionEvent> recorded = List.copyOf(events);
        events.clear();
        return recorded;
    }

    public boolean coveragePending() {
        return coverage != null && coverage.pending();
    }

    public Coverage coverage() {
        return coverage;
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

    public AdmissionPhase lastPhase() {
        return phases.stream()
                .reduce((first, second) -> second)
                .orElseThrow(AdmissionsException.AdmissionNotFound::new);
    }

    public AdmissionKind kind() {
        return lastPhase().kind();
    }

    public boolean bedRequired() {
        return lastPhase().bedRequired();
    }

    public boolean coverageMayBlockAdmission() {
        return lastPhase().coverageMayBlockAdmission();
    }

    public AdmissionStatus status() {
        return switch (statusCode) {
            case REGISTERED -> new AdmissionStatus.Registered();
            case ACTIVE -> new AdmissionStatus.Active(statusChangedAt);
            case DISCHARGED -> new AdmissionStatus.Discharged(discharge.toDischarge(statusChangedAt));
            case CANCELLED -> new AdmissionStatus.Cancelled(statusReason, statusChangedAt);
        };
    }

    private void applyStatus(AdmissionStatus status) {
        this.statusCode = status.code();
        switch (status) {
            case AdmissionStatus.Registered ignored -> {
                this.statusReason = null;
                this.statusChangedAt = null;
                this.discharge = null;
                this.deathNotice = null;
            }
            case AdmissionStatus.Active active -> {
                this.statusReason = null;
                this.statusChangedAt = active.since();
                this.discharge = null;
                this.deathNotice = null;
            }
            case AdmissionStatus.Discharged discharged -> {
                this.statusReason = null;
                this.statusChangedAt = discharged.at();
                this.discharge = DischargeDetails.of(discharged.discharge());
            }
            case AdmissionStatus.Cancelled cancelled -> {
                this.statusReason = cancelled.reason();
                this.statusChangedAt = cancelled.at();
                this.discharge = null;
                this.deathNotice = null;
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

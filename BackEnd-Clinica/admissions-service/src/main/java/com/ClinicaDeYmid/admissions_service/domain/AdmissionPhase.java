package com.ClinicaDeYmid.admissions_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admission_phases")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "kind", discriminatorType = jakarta.persistence.DiscriminatorType.STRING, length = 20)
public abstract class AdmissionPhase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false, unique = true)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admission_id", nullable = false, updatable = false)
    private Admission admission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "configuration_service_id", nullable = false, updatable = false)
    private ConfigurationService configurationService;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "opening_reason", length = 500)
    private String openingReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, insertable = false, length = 20)
    private AdmissionKind kind;

    protected AdmissionPhase() {
    }

    protected AdmissionPhase(Admission admission, ConfigurationService configurationService,
                             Instant startedAt, String openingReason) {
        this.uuid = UUID.randomUUID();
        this.admission = DomainRules.required(admission, "admission");
        this.configurationService = DomainRules.required(configurationService, "configurationService");
        this.startedAt = DomainRules.required(startedAt, "startedAt");
        this.openingReason = DomainRules.optionalText(openingReason, "openingReason", 500);
        this.kind = configurationService.kind();
    }

    static AdmissionPhase begin(Admission admission, ConfigurationService service, Instant at, String reason) {
        if (!service.status().usable()) {
            throw new AdmissionsException.RetiredConfigurationService();
        }
        return switch (service.kind()) {
            case EMERGENCY -> new EmergencyPhase(admission, service, at, reason);
            case INPATIENT -> new InpatientPhase(admission, service, at, reason);
            case OUTPATIENT -> new OutpatientPhase(admission, service, at, reason);
        };
    }

    void end(Instant at) {
        if (endedAt != null) {
            throw new AdmissionsException.PhaseAlreadyClosed();
        }
        this.endedAt = at.isBefore(startedAt) ? startedAt : at;
    }

    public abstract boolean bedRequired();

    public abstract boolean coverageMayBlockAdmission();

    public AdmissionKind kind() {
        return kind == null ? configurationService.kind() : kind;
    }

    public boolean current() {
        return endedAt == null;
    }

    public UUID uuid() {
        return uuid;
    }

    public ConfigurationService configurationService() {
        return configurationService;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant endedAt() {
        return endedAt;
    }

    public String openingReason() {
        return openingReason;
    }

    Admission admission() {
        return admission;
    }
}

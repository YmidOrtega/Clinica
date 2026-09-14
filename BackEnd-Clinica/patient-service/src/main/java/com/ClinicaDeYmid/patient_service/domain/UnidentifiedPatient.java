package com.ClinicaDeYmid.patient_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "unidentified_patients")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class UnidentifiedPatient {

    static final int EARLIEST_ESTIMATED_BIRTH_YEAR = 1900;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, unique = true, length = 36)
    private UUID uuid;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "code", nullable = false, updatable = false, unique = true, length = 14)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "sex", nullable = false, length = 20)
    private Sex sex;

    @Column(name = "estimated_birth_year", nullable = false)
    private int estimatedBirthYear;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UnidentifiedPatientStatus.Code statusCode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "identified_patient_uuid", length = 36)
    private UUID identifiedPatientUuid;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "date_of_death")
    private LocalDate dateOfDeath;

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

    @Transient
    private final List<UnidentifiedPatientEvent> events = new ArrayList<>();

    protected UnidentifiedPatient() {
    }

    public static UnidentifiedPatient register(String code, Sex sex, int estimatedBirthYear, String description, Clock clock) {
        int currentYear = LocalDate.now(clock).getYear();
        if (estimatedBirthYear < EARLIEST_ESTIMATED_BIRTH_YEAR || estimatedBirthYear > currentYear) {
            throw new PatientException.InvalidData("estimatedBirthYear",
                    "debe estar entre " + EARLIEST_ESTIMATED_BIRTH_YEAR + " y " + currentYear);
        }
        UnidentifiedPatient patient = new UnidentifiedPatient();
        patient.uuid = UUID.randomUUID();
        patient.code = UnidentifiedPatientCode.validated(code);
        patient.sex = DomainRules.required(sex, "sex");
        patient.estimatedBirthYear = estimatedBirthYear;
        patient.description = DomainRules.requiredText(description, "description", 1000);
        patient.applyStatus(new UnidentifiedPatientStatus.Unidentified(), Instant.now(clock), null);
        patient.events.add(new UnidentifiedPatientEvent.Registered());
        return patient;
    }

    public void identifyAs(Patient patient, String reason, Clock clock) {
        DomainRules.required(patient, "patient");
        if (!(patient.status() instanceof PatientStatus.Active)) {
            throw new PatientException.NotActive();
        }
        Instant now = Instant.now(clock);
        applyStatus(status().identifyAs(patient.uuid(), reason, now), now, null);
        events.add(new UnidentifiedPatientEvent.Identified(patient.uuid()));
    }

    public void revertIdentification(String reason, Clock clock) {
        UUID previousPatientUuid = identifiedPatientUuid;
        String validReason = DomainRules.requiredText(reason, "reason", 500);
        applyStatus(status().revertIdentification(), Instant.now(clock), validReason);
        events.add(new UnidentifiedPatientEvent.IdentificationReverted(previousPatientUuid));
    }

    public void recordDeath(LocalDate date, Clock clock) {
        DomainRules.required(date, "dateOfDeath");
        LocalDate today = LocalDate.now(clock);
        if (date.isAfter(today) || date.getYear() < estimatedBirthYear) {
            throw new PatientException.InvalidData("dateOfDeath", "debe estar entre el año de nacimiento estimado y hoy");
        }
        applyStatus(status().die(date), Instant.now(clock), null);
        events.add(new UnidentifiedPatientEvent.Died(date));
    }

    public List<UnidentifiedPatientEvent> pullEvents() {
        List<UnidentifiedPatientEvent> recorded = List.copyOf(events);
        events.clear();
        return recorded;
    }

    public UnidentifiedPatientStatus status() {
        return switch (statusCode) {
            case UNIDENTIFIED -> new UnidentifiedPatientStatus.Unidentified();
            case IDENTIFIED -> new UnidentifiedPatientStatus.Identified(identifiedPatientUuid, statusReason, statusChangedAt);
            case DECEASED -> new UnidentifiedPatientStatus.Deceased(dateOfDeath);
        };
    }

    public UUID uuid() {
        return uuid;
    }

    public long version() {
        return version;
    }

    public String code() {
        return code;
    }

    public Sex sex() {
        return sex;
    }

    public int estimatedBirthYear() {
        return estimatedBirthYear;
    }

    public String description() {
        return description;
    }

    public String statusReason() {
        return statusReason;
    }

    public Instant statusChangedAt() {
        return statusChangedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String createdBy() {
        return createdBy;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public String updatedBy() {
        return updatedBy;
    }

    private void applyStatus(UnidentifiedPatientStatus status, Instant changedAt, String reason) {
        statusCode = status.code();
        statusChangedAt = changedAt;
        switch (status) {
            case UnidentifiedPatientStatus.Unidentified unidentified -> {
                identifiedPatientUuid = null;
                statusReason = reason;
                dateOfDeath = null;
            }
            case UnidentifiedPatientStatus.Identified identified -> {
                identifiedPatientUuid = identified.patientUuid();
                statusReason = identified.reason();
                statusChangedAt = identified.since();
                dateOfDeath = null;
            }
            case UnidentifiedPatientStatus.Deceased deceased -> {
                identifiedPatientUuid = null;
                statusReason = null;
                dateOfDeath = deceased.dateOfDeath();
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof UnidentifiedPatient patient && uuid != null && uuid.equals(patient.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uuid);
    }

    @Override
    public String toString() {
        return "UnidentifiedPatient[" + code + "]";
    }
}

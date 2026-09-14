package com.ClinicaDeYmid.patient_service.domain;

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
@Table(name = "patients")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, unique = true, length = 36)
    private UUID uuid;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Embedded
    @AttributeOverride(name = "type", column = @Column(name = "document_type", nullable = false, length = 40))
    @AttributeOverride(name = "number", column = @Column(name = "document_number", nullable = false, length = 20))
    private IdentityDocument document;

    @Embedded
    @AttributeOverride(name = "firstNames", column = @Column(name = "first_names", nullable = false, length = 100))
    @AttributeOverride(name = "lastNames", column = @Column(name = "last_names", nullable = false, length = 100))
    private PersonName name;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "sex", nullable = false, length = 20)
    private Sex sex;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_of_origin", nullable = false, length = 2)
    private String countryOfOrigin;

    @Enumerated(EnumType.STRING)
    @Column(name = "disability", nullable = false, length = 20)
    private Disability disability;

    @Embedded
    @AttributeOverride(name = "mobile", column = @Column(name = "mobile", nullable = false, length = 16))
    @AttributeOverride(name = "phone", column = @Column(name = "phone", length = 16))
    @AttributeOverride(name = "email", column = @Column(name = "email", length = 150))
    private ContactInfo contact;

    @Embedded
    @AttributeOverride(name = "fullName", column = @Column(name = "emergency_contact_name", length = 150))
    @AttributeOverride(name = "relationship", column = @Column(name = "emergency_contact_relationship", length = 20))
    @AttributeOverride(name = "phone", column = @Column(name = "emergency_contact_phone", length = 16))
    private EmergencyContact emergencyContact;

    @Embedded
    @AttributeOverride(name = "regime", column = @Column(name = "health_regime", nullable = false, length = 20))
    @AttributeOverride(name = "affiliateType", column = @Column(name = "affiliate_type", length = 20))
    @AttributeOverride(name = "healthProviderNit", column = @Column(name = "health_provider_nit", length = 12))
    @AttributeOverride(name = "policyNumber", column = @Column(name = "policy_number", length = 50))
    private Affiliation affiliation;

    @Embedded
    @AttributeOverride(name = "department", column = @Column(name = "residence_department", nullable = false, length = 100))
    @AttributeOverride(name = "municipality", column = @Column(name = "residence_municipality", nullable = false, length = 100))
    @AttributeOverride(name = "zone", column = @Column(name = "residence_zone", nullable = false, length = 10))
    @AttributeOverride(name = "address", column = @Column(name = "residence_address", nullable = false, length = 255))
    private Residence residence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PatientStatus.Code statusCode;

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
    private final List<PatientEvent> events = new ArrayList<>();

    protected Patient() {
    }

    public static Patient register(PatientRegistration registration, Clock clock) {
        DomainRules.required(registration, "registration");
        int age = registration.demographics().ageOn(LocalDate.now(clock));
        registration.document().assertValidForAge(age);
        requireEmergencyContactForMinor(age, registration.emergencyContact());

        Patient patient = new Patient();
        patient.uuid = UUID.randomUUID();
        patient.document = registration.document();
        patient.applyDemographics(registration.demographics());
        patient.contact = registration.contact();
        patient.emergencyContact = registration.emergencyContact();
        patient.affiliation = registration.affiliation();
        patient.residence = registration.residence();
        patient.applyStatus(new PatientStatus.Active(), Instant.now(clock));
        patient.events.add(new PatientEvent.Registered());
        return patient;
    }

    public void changeDocument(IdentityDocument newDocument, Clock clock) {
        requireActive();
        DomainRules.required(newDocument, "document");
        newDocument.assertValidForAge(demographics().ageOn(LocalDate.now(clock)));
        if (!newDocument.equals(document)) {
            events.add(new PatientEvent.DocumentChanged(document));
            document = newDocument;
        }
    }

    public void correctDemographics(Demographics corrected, Clock clock) {
        requireActive();
        DomainRules.required(corrected, "demographics");
        int age = corrected.ageOn(LocalDate.now(clock));
        document.assertValidForAge(age);
        requireEmergencyContactForMinor(age, emergencyContact);
        if (!corrected.equals(demographics())) {
            applyDemographics(corrected);
            events.add(new PatientEvent.DemographicsCorrected());
        }
    }

    public void updateContact(ContactInfo newContact, EmergencyContact newEmergencyContact, Clock clock) {
        requireActive();
        DomainRules.required(newContact, "contact");
        requireEmergencyContactForMinor(demographics().ageOn(LocalDate.now(clock)), newEmergencyContact);
        contact = newContact;
        emergencyContact = newEmergencyContact;
    }

    public void updateAffiliation(Affiliation newAffiliation) {
        requireActive();
        DomainRules.required(newAffiliation, "affiliation");
        if (!newAffiliation.equals(affiliation)) {
            affiliation = newAffiliation;
            events.add(new PatientEvent.AffiliationUpdated());
        }
    }

    public void updateResidence(Residence newResidence) {
        requireActive();
        residence = DomainRules.required(newResidence, "residence");
    }

    public void deactivate(String reason, Clock clock) {
        Instant now = Instant.now(clock);
        applyStatus(status().deactivate(reason, now), now);
        events.add(new PatientEvent.Deactivated());
    }

    public void reactivate(Clock clock) {
        applyStatus(status().reactivate(), Instant.now(clock));
        events.add(new PatientEvent.Reactivated());
    }

    public void recordDeath(LocalDate date, Clock clock) {
        DomainRules.required(date, "dateOfDeath");
        if (date.isBefore(birthDate) || date.isAfter(LocalDate.now(clock))) {
            throw new PatientException.InvalidData("dateOfDeath", "debe estar entre la fecha de nacimiento y hoy");
        }
        applyStatus(status().die(date), Instant.now(clock));
        events.add(new PatientEvent.Died(date));
    }

    public List<PatientEvent> pullEvents() {
        List<PatientEvent> recorded = List.copyOf(events);
        events.clear();
        return recorded;
    }

    public PatientStatus status() {
        return switch (statusCode) {
            case ACTIVE -> new PatientStatus.Active();
            case INACTIVE -> new PatientStatus.Inactive(statusReason, statusChangedAt);
            case DECEASED -> new PatientStatus.Deceased(dateOfDeath);
        };
    }

    public Demographics demographics() {
        return new Demographics(name, birthDate, sex, countryOfOrigin, disability);
    }

    public UUID uuid() {
        return uuid;
    }

    public long version() {
        return version;
    }

    public IdentityDocument document() {
        return document;
    }

    public ContactInfo contact() {
        return contact;
    }

    public EmergencyContact emergencyContact() {
        return emergencyContact;
    }

    public Affiliation affiliation() {
        return affiliation;
    }

    public Residence residence() {
        return residence;
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

    private void requireActive() {
        if (!(status() instanceof PatientStatus.Active)) {
            throw new PatientException.NotActive();
        }
    }

    private void applyDemographics(Demographics demographics) {
        name = demographics.name();
        birthDate = demographics.birthDate();
        sex = demographics.sex();
        countryOfOrigin = demographics.countryOfOrigin();
        disability = demographics.disability();
    }

    private void applyStatus(PatientStatus status, Instant changedAt) {
        statusCode = status.code();
        statusChangedAt = changedAt;
        switch (status) {
            case PatientStatus.Active active -> {
                statusReason = null;
                dateOfDeath = null;
            }
            case PatientStatus.Inactive inactive -> {
                statusReason = inactive.reason();
                statusChangedAt = inactive.since();
                dateOfDeath = null;
            }
            case PatientStatus.Deceased deceased -> {
                statusReason = null;
                dateOfDeath = deceased.dateOfDeath();
            }
        }
    }

    private static void requireEmergencyContactForMinor(int age, EmergencyContact emergencyContact) {
        if (age < Demographics.ADULT_AGE && emergencyContact == null) {
            throw new PatientException.EmergencyContactRequired();
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Patient patient && uuid != null && uuid.equals(patient.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uuid);
    }
}

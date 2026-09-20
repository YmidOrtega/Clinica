package com.ClinicaDeYmid.practitioners_service.repository.entity;

import com.ClinicaDeYmid.practitioners_service.shared.PractitionerStatusCode;
import com.ClinicaDeYmid.practitioners_service.shared.RelationshipType;
import com.ClinicaDeYmid.practitioners_service.shared.PractitionersException;
import com.ClinicaDeYmid.practitioners_service.shared.Rules;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.type.SqlTypes;
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
@Table(name = "practitioners")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class Practitioner {

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
    private IdentityDocument document;

    @Column(name = "first_names", nullable = false, length = 100)
    private String firstNames;

    @Column(name = "last_names", nullable = false, length = 100)
    private String lastNames;

    @Embedded
    private ProfessionalRegistration registration;

    @Embedded
    private ContactInfo contact;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship", nullable = false, length = 20)
    private RelationshipType relationship;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PractitionerStatusCode statusCode;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "specialties_agreed_at")
    private Instant specialtiesAgreedAt;

    @NotAudited
    @OneToMany(mappedBy = "practitioner", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PractitionerSpecialty> specialties = new ArrayList<>();

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

    protected Practitioner() {
    }

    public static Practitioner register(IdentityDocument document, String firstNames, String lastNames,
                                        ProfessionalRegistration registration, ContactInfo contact,
                                        RelationshipType relationship) {
        Practitioner practitioner = new Practitioner();
        practitioner.uuid = UUID.randomUUID();
        practitioner.document = Rules.required(document, "document");
        practitioner.name(firstNames, lastNames);
        practitioner.registration = Rules.required(registration, "registration");
        practitioner.contact = Rules.required(contact, "contact");
        practitioner.relationship = Rules.required(relationship, "relationship");
        practitioner.statusCode = PractitionerStatusCode.ACTIVE;
        return practitioner;
    }

    public void correctIdentity(IdentityDocument newDocument, String newFirstNames, String newLastNames) {
        document = Rules.required(newDocument, "document");
        name(newFirstNames, newLastNames);
    }

    public void correctRegistration(ProfessionalRegistration newRegistration) {
        registration = Rules.required(newRegistration, "registration");
    }

    public void correctContact(ContactInfo newContact) {
        contact = Rules.required(newContact, "contact");
    }

    public void agreeRelationship(RelationshipType newRelationship) {
        relationship = Rules.required(newRelationship, "relationship");
    }

    public void assign(List<Assignment> assignments, Clock clock) {
        if (assignments == null || assignments.isEmpty()) {
            throw new PractitionersException.PrincipalSpecialtyRequired();
        }
        if (assignments.stream().filter(Assignment::principal).count() != 1) {
            throw new PractitionersException.PrincipalSpecialtyRequired();
        }
        specialties.clear();
        specialtiesAgreedAt = Instant.now(clock);
        assignments.stream()
                .map(assignment -> PractitionerSpecialty.of(this, assignment.specialty(), assignment.subSpecialty(),
                        assignment.principal()))
                .forEach(specialties::add);
    }

    public void suspend(String reason, Clock clock) {
        apply(status().suspend(reason, Instant.now(clock)));
    }

    public void retire(String reason, Clock clock) {
        apply(status().retire(reason, Instant.now(clock)));
    }

    public void reinstate() {
        apply(status().reinstate());
    }

    public PractitionerStatus status() {
        return switch (statusCode) {
            case ACTIVE -> new PractitionerStatus.Active();
            case SUSPENDED -> new PractitionerStatus.Suspended(statusReason, statusChangedAt);
            case RETIRED -> new PractitionerStatus.Retired(statusReason, statusChangedAt);
        };
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

    public String firstNames() {
        return firstNames;
    }

    public String lastNames() {
        return lastNames;
    }

    public String fullName() {
        return firstNames + " " + lastNames;
    }

    public ProfessionalRegistration registration() {
        return registration;
    }

    public ContactInfo contact() {
        return contact;
    }

    public RelationshipType relationship() {
        return relationship;
    }

    public List<PractitionerSpecialty> specialties() {
        return List.copyOf(specialties);
    }

    public Instant specialtiesAgreedAt() {
        return specialtiesAgreedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private void name(String newFirstNames, String newLastNames) {
        firstNames = Rules.atLeast(Rules.requiredText(newFirstNames, "firstNames", 100), 2, "firstNames");
        lastNames = Rules.atLeast(Rules.requiredText(newLastNames, "lastNames", 100), 2, "lastNames");
    }

    private void apply(PractitionerStatus status) {
        statusCode = status.code();
        statusReason = switch (status) {
            case PractitionerStatus.Suspended suspended -> suspended.reason();
            case PractitionerStatus.Retired retired -> retired.reason();
            case PractitionerStatus.Active ignored -> null;
        };
        statusChangedAt = switch (status) {
            case PractitionerStatus.Suspended suspended -> suspended.since();
            case PractitionerStatus.Retired retired -> retired.since();
            case PractitionerStatus.Active ignored -> null;
        };
    }

    public record Assignment(Specialty specialty, SubSpecialty subSpecialty, boolean principal) {
    }
}

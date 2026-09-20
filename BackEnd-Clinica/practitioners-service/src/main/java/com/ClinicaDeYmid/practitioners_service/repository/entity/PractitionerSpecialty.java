package com.ClinicaDeYmid.practitioners_service.repository.entity;

import com.ClinicaDeYmid.practitioners_service.shared.PractitionersException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "practitioner_specialties")
@Audited
public class PractitionerSpecialty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "practitioner_id", nullable = false, updatable = false)
    private Practitioner practitioner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "specialty_id", nullable = false, updatable = false)
    private Specialty specialty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_specialty_id", updatable = false)
    private SubSpecialty subSpecialty;

    @Column(name = "principal", nullable = false)
    private boolean principal;

    protected PractitionerSpecialty() {
    }

    static PractitionerSpecialty of(Practitioner practitioner, Specialty specialty, SubSpecialty subSpecialty,
                                    boolean principal) {
        if (!specialty.status().active()) {
            throw new PractitionersException.SpecialtyNotActiveForPractitioner(specialty.code());
        }
        if (subSpecialty != null) {
            if (!subSpecialty.specialty().uuid().equals(specialty.uuid())) {
                throw new PractitionersException.SubSpecialtyOutsideSpecialty(subSpecialty.code(), specialty.code());
            }
            if (!subSpecialty.status().active()) {
                throw new PractitionersException.SpecialtyNotActiveForPractitioner(subSpecialty.code());
            }
        }
        PractitionerSpecialty assignment = new PractitionerSpecialty();
        assignment.practitioner = practitioner;
        assignment.specialty = specialty;
        assignment.subSpecialty = subSpecialty;
        assignment.principal = principal;
        return assignment;
    }

    public Specialty specialty() {
        return specialty;
    }

    public SubSpecialty subSpecialty() {
        return subSpecialty;
    }

    public boolean principal() {
        return principal;
    }
}

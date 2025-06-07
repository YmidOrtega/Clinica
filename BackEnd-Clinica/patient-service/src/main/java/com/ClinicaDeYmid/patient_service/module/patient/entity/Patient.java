package com.ClinicaDeYmid.patient_service.module.patient.entity;

import com.ClinicaDeYmid.patient_service.module.patient.dto.AttentionDto;
import com.ClinicaDeYmid.patient_service.module.patient.dto.HealthPolicyDto;
import com.ClinicaDeYmid.patient_service.module.patient.entity.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "patients", indexes = {
        @Index(name = "idx_identification", columnList = "identification")
})
@Data
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String uuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "identification_type", nullable = false)
    private IdentificationType identificationType;

    @Column(name = "identification_number",unique = true, nullable = false)
    private String identificationNumber;

    @Column(nullable = false)
    private String name;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "birth_site_id", nullable = false)
    private Site placeOfBirth;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issuance_site_id", nullable = false)
    private Site placeOfIssuance;

    @Enumerated(EnumType.STRING)
    private Disability disability;

    @Enumerated(EnumType.STRING)
    private Language language;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "occupation_id", nullable = false)
    private Occupation occupation;

    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status")
    private MaritalStatus maritalStatus;

    @Enumerated(EnumType.STRING)
    private Religion religion;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_of_affiliation")
    private TypeOfAffiliation typeOfAffiliation;

    @Column(name = "affiliation_number")
    private String affiliationNumber;

    @Column(name = "health_provider_id", nullable = false)
    private Long healthProviderId;

    @Transient
    private HealthPolicyDto healthPolicyDetails;;

    @Column(name = "health_policy_number")
    private String healthPolicyNumber;

    @Column(name = "mothers_name")
    private String mothersName;

    @Column(name = "fathers_name")
    private String fathersName;

    @Enumerated(EnumType.STRING)
    private Zone zone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "locality_site_id", nullable = false)
    private Site locality;

    private String address;
    private String phone;
    private String mobile;

    @Column(unique = false, nullable = false)
    private String email;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.ALIVE;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void generateUuid() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
    }


    @Transient
    private List<AttentionDto> attentions = new ArrayList<>();

    public String getFullName() {
        return lastName + "," + name;
    }
}
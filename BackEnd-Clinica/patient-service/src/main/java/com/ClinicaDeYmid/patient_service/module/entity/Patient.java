package com.ClinicaDeYmid.patient_service.module.entity;

import com.ClinicaDeYmid.patient_service.module.dto.AttentionDto;
import com.ClinicaDeYmid.patient_service.module.dto.GetHealthProviderDto;
import com.ClinicaDeYmid.patient_service.module.enums.*;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Represents a patient in the healthcare system.")
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Unique identifier of the patient.", example = "1")
    private Long id;

    @Column(unique = true, nullable = false)
    @Schema(description = "Unique identification number of the patient.", example = "123456789")
    private String uuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "identification_type", nullable = false)
    @Schema(description = "Type of identification document for the patient.", example = "CITIZEN_ID")
    private IdentificationType identificationType;

    @Column(name = "identification_number",unique = true, nullable = false)
    @Schema(description = "Identification number of the patient.", example = "987654321")
    private String identificationNumber;

    @Column(nullable = false)
    @Schema(description = "Full name of the patient.", example = "John Doe")
    private String name;

    @Column(name = "last_name", nullable = false)
    @Schema(description = "Last name of the patient.", example = "Doe")
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    @Schema(description = "Date of birth of the patient.", example = "1990-01-01")
    private LocalDate dateOfBirth;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "birth_site_id", nullable = false)
    @Schema(description = "Place of birth of the patient.")
    private Site placeOfBirth;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issuance_site_id", nullable = false)
    @Schema(description = "Place of issuance of the patient's identification document.")
    private Site placeOfIssuance;

    @Enumerated(EnumType.STRING)
    @Schema(description = "Disability status of the patient.", example = "NONE")
    private Disability disability;

    @Enumerated(EnumType.STRING)
    @Schema(description = "Language spoken by the patient.", example = "SPANISH")
    private Language language;

    @Enumerated(EnumType.STRING)
    @Schema(description = "Gender of the patient.", example = "MASCULINE")
    private Gender gender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "occupation_id", nullable = false)
    @Schema(description = "Occupation of the patient.")
    private Occupation occupation;

    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status")
    @Schema(description = "Marital status of the patient.", example = "SINGLE")
    private MaritalStatus maritalStatus;

    @Enumerated(EnumType.STRING)
    @Schema(description = "Religion of the patient.", example = "CHRISTIANITY")
    private Religion religion;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_of_affiliation")
    @Schema(description = "Type of affiliation the patient has with a healthcare provider.", example = "EMPLOYED")
    private TypeOfAffiliation typeOfAffiliation;

    @Column(name = "affiliation_number")
    @Schema(description = "Affiliation number of the patient with a healthcare provider.", example = "AFF123456")
    private String affiliationNumber;

    @Column(name = "health_provider_nit", nullable = false)
    @Schema(description = "NIT (Tax Identification Number) of the healthcare provider associated with the patient.", example = "123456789")
    private String healthProviderNit;

    @Transient
    @Schema(description = "DTO containing details of the healthcare provider associated with the patient.")
    private GetHealthProviderDto getHealthProviderDto;;

    @Column(name = "health_policy_number")
    @Schema(description = "Health policy number of the patient.", example = "POL123456")
    private String healthPolicyNumber;

    @Column(name = "mothers_name")
    @Schema(description = "Name of the patient's mother.", example = "Jane Doe")
    private String mothersName;

    @Column(name = "fathers_name")
    @Schema(description = "Name of the patient's father.", example = "John Smith")
    private String fathersName;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_type", nullable = false)
    private Zone zone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "locality_site_id", nullable = false)
    @Schema(description = "Locality of the patient.")
    private Site locality;

    @Schema(description = "Address of the patient.", example = "123 Main St, Springfield")
    private String address;
    @Schema(description = "Phone number of the patient.", example = "+1234567890")
    private String phone;
    @Schema(description = "Phone number of the patient.", example = "+1234567890")
    private String mobile;

    @Column(unique = false, nullable = false)
    @Schema(description = "Email address of the patient.", example = "patient@example.com")
    private String email;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    @Schema(description = "Timestamp when the patient record was created.")
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Schema(description = "Status of the patient in the healthcare system.", example = "ALIVE")
    private Status status = Status.ALIVE;

    @Column(name = "updated_at", nullable = false)
@Schema(description = "Timestamp when the patient record was last updated.")
    private LocalDateTime updatedAt;

    @PrePersist
    private void generateUuid() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
    }


    @Transient
    @Schema(description = "List of attentions associated with the patient.")
    private List<AttentionDto> attentions = new ArrayList<>();

    public String getFullName() {
        return lastName + "," + name;
    }
}
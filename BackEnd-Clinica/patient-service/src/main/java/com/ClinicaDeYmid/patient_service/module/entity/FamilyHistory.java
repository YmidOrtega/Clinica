package com.ClinicaDeYmid.patient_service.module.entity;

import com.ClinicaDeYmid.patient_service.module.enums.FamilyRelationship;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "family_histories", indexes = {
        @Index(name = "idx_family_history_patient", columnList = "patient_id"),
        @Index(name = "idx_family_history_relationship", columnList = "relationship"),
        @Index(name = "idx_family_history_active", columnList = "active")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@Schema(description = "Antecedente familiar médico del paciente")
public class FamilyHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "ID único del antecedente familiar", example = "1")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    @Schema(description = "Paciente al que pertenece este antecedente")
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship", nullable = false, length = 30)
    @Schema(description = "Relación familiar con el paciente", example = "FATHER")
    private FamilyRelationship relationship;

    @Column(name = "relative_name", length = 200)
    @Schema(description = "Nombre del familiar (opcional)", example = "Juan Pérez")
    private String relativeName;

    @Column(name = "condition_name", nullable = false, length = 200)
    @Schema(description = "Condición o enfermedad del familiar", example = "Diabetes Tipo 2")
    private String conditionName;

    @Column(name = "icd10_code", length = 10)
    @Schema(description = "Código CIE-10 de la condición", example = "E11")
    private String icd10Code;

    @Column(name = "age_of_onset")
    @Schema(description = "Edad a la que se manifestó la condición", example = "45")
    private Integer ageOfOnset;

    @Column(name = "current_status", length = 50)
    @Schema(description = "Estado actual del familiar", example = "ALIVE")
    private String currentStatus;

    @Column(name = "age_at_death")
    @Schema(description = "Edad al momento del fallecimiento (si aplica)", example = "70")
    private Integer ageAtDeath;

    @Column(name = "cause_of_death", length = 200)
    @Schema(description = "Causa de fallecimiento (si aplica)", example = "Complicaciones cardíacas")
    private String causeOfDeath;

    @Column(name = "severity", length = 50)
    @Schema(description = "Severidad de la condición", example = "MODERATE")
    private String severity;

    @Column(name = "treatment_received", columnDefinition = "TEXT")
    @Schema(description = "Tratamiento que recibió el familiar")
    private String treatmentReceived;

    @Column(name = "genetic_risk", nullable = false)
    @Schema(description = "Indica si existe riesgo genético conocido")
    @Builder.Default
    private Boolean geneticRisk = false;

    @Column(name = "screening_recommended", nullable = false)
    @Schema(description = "Indica si se recomienda screening preventivo al paciente")
    @Builder.Default
    private Boolean screeningRecommended = false;

    @Column(name = "screening_details", columnDefinition = "TEXT")
    @Schema(description = "Detalles del screening recomendado")
    private String screeningDetails;

    @Column(name = "notes", columnDefinition = "TEXT")
    @Schema(description = "Notas adicionales sobre el antecedente")
    private String notes;

    @Column(name = "active", nullable = false)
    @Schema(description = "Indica si el antecedente está activo")
    @Builder.Default
    private Boolean active = true;

    @Column(name = "verified", nullable = false)
    @Schema(description = "Indica si el antecedente ha sido verificado médicamente")
    @Builder.Default
    private Boolean verified = false;

    @Column(name = "verified_by", length = 200)
    @Schema(description = "Médico que verificó el antecedente")
    private String verifiedBy;

    @Column(name = "verified_date")
    @Schema(description = "Fecha de verificación")
    private LocalDateTime verifiedDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @Schema(description = "Fecha de creación del registro")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    @Schema(description = "Fecha de última actualización")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    @Schema(description = "Usuario que creó el registro")
    private Long createdBy;

    @Column(name = "updated_by")
    @Schema(description = "Usuario que actualizó el registro")
    private Long updatedBy;
}
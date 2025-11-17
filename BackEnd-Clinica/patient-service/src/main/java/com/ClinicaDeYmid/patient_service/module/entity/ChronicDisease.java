package com.ClinicaDeYmid.patient_service.module.entity;

import com.ClinicaDeYmid.patient_service.module.enums.DiseaseSeverity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "chronic_diseases", indexes = {
        @Index(name = "idx_chronic_disease_patient", columnList = "patient_id"),
        @Index(name = "idx_chronic_disease_active", columnList = "active"),
        @Index(name = "idx_chronic_disease_severity", columnList = "severity"),
        @Index(name = "idx_chronic_disease_icd10", columnList = "icd10_code")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@Schema(description = "Enfermedad crónica del paciente")
public class ChronicDisease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "ID único de la enfermedad crónica", example = "1")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    @Schema(description = "Paciente al que pertenece esta enfermedad")
    private Patient patient;

    @Column(name = "disease_name", nullable = false, length = 200)
    @Schema(description = "Nombre de la enfermedad", example = "Diabetes Mellitus Tipo 2")
    private String diseaseName;

    @Column(name = "icd10_code", length = 10)
    @Schema(description = "Código CIE-10 de la enfermedad", example = "E11")
    private String icd10Code;

    @Column(name = "diagnosed_date")
    @Schema(description = "Fecha de diagnóstico", example = "2018-03-20")
    private LocalDate diagnosedDate;

    @Column(name = "diagnosed_by", length = 200)
    @Schema(description = "Médico que diagnosticó la enfermedad", example = "Dr. María López")
    private String diagnosedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 30)
    @Schema(description = "Severidad de la enfermedad", example = "CONTROLLED")
    private DiseaseSeverity severity;

    @Column(name = "treatment_plan", columnDefinition = "TEXT")
    @Schema(description = "Plan de tratamiento actual")
    private String treatmentPlan;

    @Column(name = "complications", columnDefinition = "TEXT")
    @Schema(description = "Complicaciones conocidas de la enfermedad")
    private String complications;

    @Column(name = "last_flare_date")
    @Schema(description = "Fecha del último brote o crisis", example = "2024-01-10")
    private LocalDate lastFlareDate;

    @Column(name = "monitoring_frequency", length = 100)
    @Schema(description = "Frecuencia de monitoreo recomendada", example = "Cada 3 meses")
    private String monitoringFrequency;

    @Column(name = "notes", columnDefinition = "TEXT")
    @Schema(description = "Notas adicionales sobre la enfermedad")
    private String notes;

    @Column(name = "active", nullable = false)
    @Schema(description = "Indica si la enfermedad está activa actualmente")
    @Builder.Default
    private Boolean active = true;

    @Column(name = "requires_specialist", nullable = false)
    @Schema(description = "Indica si requiere seguimiento por especialista")
    @Builder.Default
    private Boolean requiresSpecialist = false;

    @Column(name = "specialist_type", length = 100)
    @Schema(description = "Tipo de especialista requerido", example = "Endocrinólogo")
    private String specialistType;

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
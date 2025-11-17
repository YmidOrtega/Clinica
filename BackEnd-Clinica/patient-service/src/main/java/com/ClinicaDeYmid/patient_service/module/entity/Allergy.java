package com.ClinicaDeYmid.patient_service.module.entity;

import com.ClinicaDeYmid.patient_service.module.enums.AllergyReactionType;
import com.ClinicaDeYmid.patient_service.module.enums.AllergySeverity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "allergies", indexes = {
        @Index(name = "idx_allergy_patient", columnList = "patient_id"),
        @Index(name = "idx_allergy_active", columnList = "active"),
        @Index(name = "idx_allergy_severity", columnList = "severity")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@Schema(description = "Alergia registrada del paciente")
public class Allergy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "ID único de la alergia", example = "1")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    @Schema(description = "Paciente al que pertenece esta alergia")
    private Patient patient;

    @Column(name = "allergen", nullable = false, length = 200)
    @Schema(description = "Alérgeno que causa la reacción", example = "Penicilina")
    private String allergen;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 30)
    @Schema(description = "Severidad de la alergia", example = "SEVERE")
    private AllergySeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", length = 50)
    @Schema(description = "Tipo de reacción alérgica", example = "RESPIRATORY")
    private AllergyReactionType reactionType;

    @Column(name = "symptoms", columnDefinition = "TEXT")
    @Schema(description = "Síntomas específicos de la reacción", example = "Dificultad para respirar, sarpullido")
    private String symptoms;

    @Column(name = "diagnosed_date")
    @Schema(description = "Fecha en que se diagnosticó la alergia", example = "2020-05-15")
    private LocalDate diagnosedDate;

    @Column(name = "diagnosed_by", length = 200)
    @Schema(description = "Médico que diagnosticó la alergia", example = "Dr. Juan Pérez")
    private String diagnosedBy;

    @Column(name = "treatment", columnDefinition = "TEXT")
    @Schema(description = "Tratamiento recomendado para la alergia")
    private String treatment;

    @Column(name = "notes", columnDefinition = "TEXT")
    @Schema(description = "Notas adicionales sobre la alergia")
    private String notes;

    @Column(name = "active", nullable = false)
    @Schema(description = "Indica si la alergia está activa actualmente")
    @Builder.Default
    private Boolean active = true;

    @Column(name = "verified", nullable = false)
    @Schema(description = "Indica si la alergia ha sido verificada médicamente")
    @Builder.Default
    private Boolean verified = false;

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
package com.ClinicaDeYmid.patient_service.module.entity;

import com.ClinicaDeYmid.patient_service.module.enums.MedicationRoute;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "current_medications", indexes = {
        @Index(name = "idx_medication_patient", columnList = "patient_id"),
        @Index(name = "idx_medication_active", columnList = "active"),
        @Index(name = "idx_medication_end_date", columnList = "end_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@Schema(description = "Medicamento actual del paciente")
public class CurrentMedication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "ID único del medicamento", example = "1")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    @Schema(description = "Paciente que toma este medicamento")
    private Patient patient;

    @Column(name = "medication_name", nullable = false, length = 200)
    @Schema(description = "Nombre del medicamento", example = "Metformina")
    private String medicationName;

    @Column(name = "generic_name", length = 200)
    @Schema(description = "Nombre genérico del medicamento", example = "Metformin Hydrochloride")
    private String genericName;

    @Column(name = "dosage", nullable = false, length = 100)
    @Schema(description = "Dosis del medicamento", example = "500mg")
    private String dosage;

    @Column(name = "frequency", nullable = false, length = 100)
    @Schema(description = "Frecuencia de administración", example = "Cada 12 horas")
    private String frequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "route", length = 30)
    @Schema(description = "Vía de administración", example = "ORAL")
    private MedicationRoute route;

    @Column(name = "instructions", columnDefinition = "TEXT")
    @Schema(description = "Instrucciones específicas de uso", example = "Tomar con alimentos")
    private String instructions;

    @Column(name = "start_date", nullable = false)
    @Schema(description = "Fecha de inicio del tratamiento", example = "2024-01-15")
    private LocalDate startDate;

    @Column(name = "end_date")
    @Schema(description = "Fecha de fin del tratamiento (si aplica)", example = "2024-07-15")
    private LocalDate endDate;

    @Column(name = "prescribed_by", nullable = false, length = 200)
    @Schema(description = "Médico que prescribió el medicamento", example = "Dr. Carlos Ramírez")
    private String prescribedBy;

    @Column(name = "prescribed_by_id")
    @Schema(description = "ID del médico que prescribió", example = "123")
    private Long prescribedById;

    @Column(name = "prescription_number", length = 100)
    @Schema(description = "Número de la receta médica", example = "RX-2024-001234")
    private String prescriptionNumber;

    @Column(name = "pharmacy", length = 200)
    @Schema(description = "Farmacia donde se surte el medicamento", example = "Farmacia San Rafael")
    private String pharmacy;

    @Column(name = "refills_remaining")
    @Schema(description = "Número de resurtidos restantes", example = "3")
    private Integer refillsRemaining;

    @Column(name = "reason", columnDefinition = "TEXT")
    @Schema(description = "Razón de la prescripción", example = "Control de diabetes tipo 2")
    private String reason;

    @Column(name = "side_effects", columnDefinition = "TEXT")
    @Schema(description = "Efectos secundarios conocidos o experimentados")
    private String sideEffects;

    @Column(name = "interactions", columnDefinition = "TEXT")
    @Schema(description = "Interacciones conocidas con otros medicamentos")
    private String interactions;

    @Column(name = "notes", columnDefinition = "TEXT")
    @Schema(description = "Notas adicionales sobre el medicamento")
    private String notes;

    @Column(name = "active", nullable = false)
    @Schema(description = "Indica si el medicamento está activo actualmente")
    @Builder.Default
    private Boolean active = true;

    @Column(name = "discontinued", nullable = false)
    @Schema(description = "Indica si el medicamento fue descontinuado")
    @Builder.Default
    private Boolean discontinued = false;

    @Column(name = "discontinued_date")
    @Schema(description = "Fecha en que se descontinuó el medicamento")
    private LocalDate discontinuedDate;

    @Column(name = "discontinued_reason", length = 500)
    @Schema(description = "Razón por la que se descontinuó")
    private String discontinuedReason;

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

    /**
     * Verifica si el medicamento está vencido
     */
    @Transient
    public boolean isExpired() {
        return endDate != null && endDate.isBefore(LocalDate.now());
    }

    /**
     * Verifica si el medicamento necesita resurtido
     */
    @Transient
    public boolean needsRefill() {
        return active && !discontinued && (refillsRemaining == null || refillsRemaining <= 1);
    }
}
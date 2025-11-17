package com.ClinicaDeYmid.patient_service.module.entity;

import com.ClinicaDeYmid.patient_service.module.enums.BloodType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "medical_histories", indexes = {
        @Index(name = "idx_medical_history_patient", columnList = "patient_id"),
        @Index(name = "idx_medical_history_updated", columnList = "updated_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@Schema(description = "Historia clínica completa del paciente")
public class MedicalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "ID único de la historia clínica", example = "1")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false, unique = true)
    @Schema(description = "Paciente al que pertenece esta historia clínica")
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_type", length = 20)
    @Schema(description = "Tipo de sangre del paciente", example = "O_POSITIVE")
    private BloodType bloodType;

    @Column(name = "rh_factor", length = 10)
    @Schema(description = "Factor RH", example = "POSITIVE")
    private String rhFactor;

    @Column(name = "blood_pressure", length = 20)
    @Schema(description = "Presión arterial habitual", example = "120/80")
    private String bloodPressure;

    @Column(name = "weight")
    @Schema(description = "Peso en kilogramos", example = "70.5")
    private Double weight;

    @Column(name = "height")
    @Schema(description = "Altura en centímetros", example = "175.0")
    private Double height;

    @Column(name = "bmi")
    @Schema(description = "Índice de masa corporal calculado", example = "23.0")
    private Double bmi;

    @Column(name = "smoking_status", length = 50)
    @Schema(description = "Estado de fumador", example = "NON_SMOKER")
    private String smokingStatus;

    @Column(name = "alcohol_consumption", length = 50)
    @Schema(description = "Consumo de alcohol", example = "OCCASIONAL")
    private String alcoholConsumption;

    @Column(name = "exercise_frequency", length = 50)
    @Schema(description = "Frecuencia de ejercicio", example = "REGULAR")
    private String exerciseFrequency;

    @Column(name = "diet_type", length = 50)
    @Schema(description = "Tipo de dieta", example = "BALANCED")
    private String dietType;

    @Column(name = "notes", columnDefinition = "TEXT")
    @Schema(description = "Notas adicionales sobre la historia médica")
    private String notes;

    @Column(name = "last_checkup_date")
    @Schema(description = "Fecha del último chequeo médico", example = "2024-01-15")
    private LocalDate lastCheckupDate;

    @Column(name = "next_checkup_date")
    @Schema(description = "Fecha del próximo chequeo recomendado", example = "2024-07-15")
    private LocalDate nextCheckupDate;

    @Column(name = "has_insurance")
    @Schema(description = "Indica si el paciente tiene seguro médico")
    private Boolean hasInsurance;

    @Column(name = "insurance_provider", length = 200)
    @Schema(description = "Proveedor del seguro médico", example = "Sanitas EPS")
    private String insuranceProvider;

    @Column(name = "insurance_number", length = 100)
    @Schema(description = "Número de póliza del seguro", example = "POL123456")
    private String insuranceNumber;

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
     * Calcula el IMC basado en peso y altura
     */
    @PrePersist
    @PreUpdate
    public void calculateBmi() {
        if (weight != null && height != null && height > 0) {
            double heightInMeters = height / 100.0;
            this.bmi = weight / (heightInMeters * heightInMeters);
            this.bmi = Math.round(this.bmi * 100.0) / 100.0;
        }
    }
}
package com.ClinicaDeYmid.patient_service.module.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "vaccination_records", indexes = {
        @Index(name = "idx_vaccination_patient", columnList = "patient_id"),
        @Index(name = "idx_vaccination_date", columnList = "administered_date"),
        @Index(name = "idx_vaccination_next_dose", columnList = "next_dose_date"),
        @Index(name = "idx_vaccination_vaccine", columnList = "vaccine_name")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@Schema(description = "Registro de vacunación del paciente")
public class VaccinationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "ID único del registro de vacunación", example = "1")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    @Schema(description = "Paciente al que pertenece este registro")
    private Patient patient;

    @Column(name = "vaccine_name", nullable = false, length = 200)
    @Schema(description = "Nombre de la vacuna", example = "COVID-19 mRNA")
    private String vaccineName;

    @Column(name = "vaccine_type", length = 100)
    @Schema(description = "Tipo de vacuna", example = "mRNA")
    private String vaccineType;

    @Column(name = "manufacturer", length = 200)
    @Schema(description = "Fabricante de la vacuna", example = "Pfizer-BioNTech")
    private String manufacturer;

    @Column(name = "dose_number", nullable = false)
    @Schema(description = "Número de dosis", example = "1")
    private Integer doseNumber;

    @Column(name = "total_doses_required")
    @Schema(description = "Total de dosis requeridas en el esquema", example = "2")
    private Integer totalDosesRequired;

    @Column(name = "lot_number", length = 100)
    @Schema(description = "Número de lote de la vacuna", example = "LOT123456")
    private String lotNumber;

    @Column(name = "administered_date", nullable = false)
    @Schema(description = "Fecha de administración", example = "2024-01-15")
    private LocalDate administeredDate;

    @Column(name = "next_dose_date")
    @Schema(description = "Fecha programada para la siguiente dosis", example = "2024-02-15")
    private LocalDate nextDoseDate;

    @Column(name = "administered_by", nullable = false, length = 200)
    @Schema(description = "Profesional que administró la vacuna", example = "Enfermera María González")
    private String administeredBy;

    @Column(name = "administered_by_id")
    @Schema(description = "ID del profesional que administró", example = "456")
    private Long administeredById;

    @Column(name = "location", nullable = false, length = 200)
    @Schema(description = "Lugar donde se administró la vacuna", example = "Centro de Salud San José")
    private String location;

    @Column(name = "site_of_administration", length = 100)
    @Schema(description = "Sitio anatómico de administración", example = "Brazo izquierdo - músculo deltoides")
    private String siteOfAdministration;

    @Column(name = "route", length = 50)
    @Schema(description = "Vía de administración", example = "Intramuscular")
    private String route;

    @Column(name = "expiration_date")
    @Schema(description = "Fecha de vencimiento de la vacuna", example = "2025-12-31")
    private LocalDate expirationDate;

    @Column(name = "adverse_reactions", columnDefinition = "TEXT")
    @Schema(description = "Reacciones adversas reportadas")
    private String adverseReactions;

    @Column(name = "had_reaction", nullable = false)
    @Schema(description = "Indica si hubo alguna reacción")
    @Builder.Default
    private Boolean hadReaction = false;

    @Column(name = "reaction_severity", length = 50)
    @Schema(description = "Severidad de la reacción (si aplica)", example = "MILD")
    private String reactionSeverity;

    @Column(name = "contraindications", columnDefinition = "TEXT")
    @Schema(description = "Contraindicaciones conocidas")
    private String contraindications;

    @Column(name = "notes", columnDefinition = "TEXT")
    @Schema(description = "Notas adicionales sobre la vacunación")
    private String notes;

    @Column(name = "verified", nullable = false)
    @Schema(description = "Indica si el registro ha sido verificado")
    @Builder.Default
    private Boolean verified = false;

    @Column(name = "verified_by", length = 200)
    @Schema(description = "Profesional que verificó el registro")
    private String verifiedBy;

    @Column(name = "verified_date")
    @Schema(description = "Fecha de verificación")
    private LocalDateTime verifiedDate;

    @Column(name = "certificate_number", length = 100)
    @Schema(description = "Número de certificado de vacunación", example = "CERT-2024-001234")
    private String certificateNumber;

    @Column(name = "valid_for_travel", nullable = false)
    @Schema(description = "Indica si es válida para viajes internacionales")
    @Builder.Default
    private Boolean validForTravel = false;

    @Column(name = "booster", nullable = false)
    @Schema(description = "Indica si es una dosis de refuerzo")
    @Builder.Default
    private Boolean booster = false;

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
     * Verifica si la vacuna ha expirado
     */
    @Transient
    public boolean isExpired() {
        return expirationDate != null && expirationDate.isBefore(LocalDate.now());
    }

    /**
     * Verifica si necesita la siguiente dosis
     */
    @Transient
    public boolean needsNextDose() {
        return nextDoseDate != null && nextDoseDate.isBefore(LocalDate.now().plusDays(30)) && doseNumber < totalDosesRequired;
    }

    /**
     * Verifica si el esquema de vacunación está completo
     */
    @Transient
    public boolean isSchemeComplete() {
        return totalDosesRequired != null && doseNumber.equals(totalDosesRequired);
    }
}
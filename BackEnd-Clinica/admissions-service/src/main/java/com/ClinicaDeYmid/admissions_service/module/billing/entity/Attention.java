package com.ClinicaDeYmid.module.billing.model;

import com.ClinicaDeYmid.module.billing.model.emun.Cause;
import com.ClinicaDeYmid.module.billing.model.emun.EntryRoute;
import com.ClinicaDeYmid.module.billing.model.emun.ServiceType;
import com.ClinicaDeYmid.module.patient.model.HealthPolicy;
import com.ClinicaDeYmid.module.patient.model.Patient;
import com.ClinicaDeYmid.module.patient.model.Site;
import com.ClinicaDeYmid.module.patient.model.emun.Zone;
import com.ClinicaDeYmid.module.user.model.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.antlr.v4.runtime.misc.NotNull;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "attentions", indexes = {
        @Index(name = "idx_attention_patient", columnList = "patient_id"),
        @Index(name = "idx_attention_doctor", columnList = "doctor_id"),
        @Index(name = "idx_attention_created", columnList = "created_at"),
        @Index(name = "idx_attention_active", columnList = "active"),
        @Index(name = "idx_attention_status", columnList = "active_attention")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Builder
public class Attention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(name = "attention_movements", nullable = false)
    private boolean attentionMovements = true;

    @Builder.Default
    @Column(name = "active_attention", nullable = false)
    private boolean activeAttention = true;

    @Builder.Default
    @Column(name = "pre_admission", nullable = false)
    private boolean preAdmission = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean invoiced = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_attention_user"))
    private User user;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_of_attention_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_attention_type"))
    @NotNull(message = "El tipo de atención es obligatorio")
    private com.ClinicaDeYmid.module.billing.model.TypeOfAttention typeOfAttention;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_attention_patient"))
    @NotNull(message = "El paciente es obligatorio")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "health_policy_id",
            foreignKey = @ForeignKey(name = "fk_attention_health_policy"))
    private Long healthProviderId;;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_attention_doctor"))
    @NotNull(message = "El doctor es obligatorio")
    private Doctor doctor;

    // Información del acompañante
    @Size(max = 100, message = "El nombre del acompañante no puede exceder 100 caracteres")
    @Column(name = "companion_name", length = 100)
    private String companionName;

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$",
            message = "El teléfono del acompañante no tiene un formato válido")
    @Column(name = "companion_phone", length = 20)
    private String companionPhone;

    @Size(max = 50, message = "La relación no puede exceder 50 caracteres")
    @Column(length = 50)
    private String relationship;

    @Size(max = 1000, message = "Las observaciones no pueden exceder 1000 caracteres")
    @Column(length = 1000)
    private String observations;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Ubicación y zona
    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Zone zone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id",
            foreignKey = @ForeignKey(name = "fk_attention_site"))
    private Site site;

    // Clasificación de la atención
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_route", length = 50)
    private EntryRoute entryRoute;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_service", length = 50)
    private ServiceType entryService;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_service", length = 50)
    private ServiceType locationService;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "La causa de atención es obligatoria")
    @Column(nullable = false, length = 50)
    private Cause cause;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // Métodos de utilidad
    public boolean hasCompanion() {
        return companionName != null && !companionName.trim().isEmpty();
    }

    public String getAttentionSummary() {
        return String.format("Atención %d - Paciente: %s, Doctor: %s, Fecha: %s",
                id,
                patient != null ? patient.getFullName() : "N/A",
                doctor != null ? doctor.getFullName() : "N/A",
                createdAt != null ? createdAt.toLocalDate() : "N/A");
    }
}
package com.ClinicaDeYmid.admissions_service.module.entity;

import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;
import com.ClinicaDeYmid.admissions_service.module.enums.Cause;
import com.ClinicaDeYmid.admissions_service.module.enums.TriageLevel;
import com.ClinicaDeYmid.admissions_service.module.enums.UserActionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "attentions", indexes = {
        @Index(name = "idx_attention_patient_id", columnList = "patientId"),
        @Index(name = "idx_attention_doctor_id", columnList = "doctorId"),
        @Index(name = "idx_attention_health_provider_id", columnList = "healthProviderId")
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

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Builder.Default
    @Column(name = "has_movements", nullable = false)
    private boolean hasMovements = true;

    @Builder.Default
    @Column(name = "is_active_attention", nullable = false)
    private boolean isActiveAttention = true;

    @Builder.Default
    @Column(name = "is_pre_admission", nullable = false)
    private boolean isPreAdmission = false;

    @Builder.Default
    @Column(name = "invoiced", nullable = false)
    private boolean invoiced = false;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "health_provider_id", nullable = false)
    private Long healthProviderId;

    @Column(name = "invoice_number")
    private Long invoiceNumber;

    @OneToMany(mappedBy = "attention", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("actionTimestamp ASC")
    private List<AttentionUserHistory> userHistory = new ArrayList<>();

    @OneToMany(mappedBy = "attention", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Authorization> authorizations;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "configuration_service_id")
    private ConfigurationService configurationService;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "admission_date_time")
    private LocalDateTime admissionDateTime;

    @Column(name = "discharge_date_time")
    private LocalDateTime dischargeDateTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AttentionStatus status;

    @Column(name = "entry_method", length = 50)
    private String entryMethod;

    @Column(name = "referring_entity", length = 100)
    private String referringEntity;

    @Column(name = "is_referral")
    private Boolean isReferral;

    @Column(name = "main_diagnosis_code", length = 20)
    private String mainDiagnosisCode;

    @ElementCollection
    @CollectionTable(name = "attention_secondary_diagnoses", joinColumns = @JoinColumn(name = "attention_id"))
    @Column(name = "diagnosis_code")
    private List<String> secondaryDiagnosisCodes;

    @Enumerated(EnumType.STRING)
    @Column(name = "triage_level", length = 20)
    private TriageLevel triageLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Cause cause;

    @Embedded
    private Companion companion;

    @Column(length = 1000)
    private String observations;

    @Column(length = 1000)
    private String billingObservations;

    @Transient
    public Long getCreatedByUserId() {
        return userHistory.stream()
                .filter(h -> h.getActionType() == UserActionType.CREATED)
                .findFirst()
                .map(AttentionUserHistory::getUserId)
                .orElse(null);
    }

    @Transient
    public Long getLastUpdatedByUserId() {
        return userHistory.stream()
                .filter(h -> h.getActionType() == UserActionType.UPDATED)
                .reduce((first, second) -> second) // Obtiene el último
                .map(AttentionUserHistory::getUserId)
                .orElse(null);
    }

    @Transient
    public Long getInvoicedByUserId() {
        return userHistory.stream()
                .filter(h -> h.getActionType() == UserActionType.INVOICED)
                .findFirst()
                .map(AttentionUserHistory::getUserId)
                .orElse(null);
    }

    public void addUserAction(Long userId, UserActionType actionType, String observations) {
        AttentionUserHistory history = AttentionUserHistory.builder()
                .attention(this)
                .userId(userId)
                .actionType(actionType)
                .observations(observations)
                .build();

        if (this.userHistory == null) {
            this.userHistory = new ArrayList<>();
        }
        this.userHistory.add(history);
    }
}

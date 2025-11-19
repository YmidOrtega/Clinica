package com.ClinicaDeYmid.suppliers_service.module.entity;

import com.ClinicaDeYmid.suppliers_service.module.enums.UnavailabilityType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "doctor_unavailability",
        indexes = {
                @Index(name = "idx_unavailability_doctor", columnList = "doctor_id"),
                @Index(name = "idx_unavailability_dates", columnList = "start_date, end_date"),
                @Index(name = "idx_unavailability_type", columnList = "type")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class DoctorUnavailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private UnavailabilityType type;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(length = 500)
    private String reason;

    @Builder.Default
    @Column(nullable = false)
    private Boolean approved = false;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Valida que la fecha de fin sea posterior o igual a la de inicio
     */
    @PrePersist
    @PreUpdate
    private void validateDates() {
        if (endDate != null && startDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be after or equal to start date");
        }
    }

    /**
     * Verifica si el periodo de no disponibilidad está activo en una fecha específica
     */
    public boolean isActiveOn(LocalDate date) {
        if (!approved) {
            return false;
        }
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    /**
     * Aprueba el periodo de no disponibilidad
     */
    public void approve(String approverUsername) {
        this.approved = true;
        this.approvedBy = approverUsername;
        this.approvedAt = LocalDateTime.now();
    }

    /**
     * Revoca la aprobación
     */
    public void revokeApproval() {
        this.approved = false;
        this.approvedBy = null;
        this.approvedAt = null;
    }
}
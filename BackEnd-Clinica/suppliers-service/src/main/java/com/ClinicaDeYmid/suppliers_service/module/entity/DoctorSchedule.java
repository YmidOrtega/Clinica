package com.ClinicaDeYmid.suppliers_service.module.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Entity
@Table(name = "doctor_schedules",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_doctor_day_time",
                        columnNames = {"doctor_id", "day_of_week", "start_time", "end_time"})
        },
        indexes = {
                @Index(name = "idx_schedule_doctor", columnList = "doctor_id"),
                @Index(name = "idx_schedule_day", columnList = "day_of_week"),
                @Index(name = "idx_schedule_active", columnList = "active")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class DoctorSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 20)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Valida que el horario de fin sea posterior al de inicio
     */
    @PrePersist
    @PreUpdate
    private void validateTimes() {
        if (endTime != null && startTime != null && !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("End time must be after start time");
        }
    }

    /**
     * Verifica si el doctor está disponible en un horario específico
     */
    public boolean isAvailableAt(DayOfWeek day, LocalTime time) {
        if (!active || !dayOfWeek.equals(day)) {
            return false;
        }
        return !time.isBefore(startTime) && !time.isAfter(endTime);
    }
}
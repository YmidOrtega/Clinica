package com.ClinicaDeYmid.suppliers_service.module.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "doctors", indexes = {
        @Index(name = "idx_doctor_license", columnList = "license_number"),
        @Index(name = "idx_doctor_email", columnList = "email"),
        @Index(name = "idx_doctor_active", columnList = "active"),
        @Index(name = "idx_doctor_provider_code", columnList = "provider_code"),
        @Index(name = "idx_doctor_identification", columnList = "identification_number")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Builder
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_code", nullable = false, unique = true)
    private Integer providerCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "identification_number", nullable = false, unique = true, length = 20)
    private String identificationNumber;

    @Builder.Default
    @ManyToMany(cascade = CascadeType.MERGE, fetch = FetchType.LAZY)
    @JoinTable(
            name = "doctor_specialties",
            joinColumns = @JoinColumn(name = "doctor_id"),
            inverseJoinColumns = @JoinColumn(name = "specialty_id")
    )
    @JsonManagedReference("doctor-specialties")
    private Set<Speciality> specialties = new HashSet<>();

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "doctor_subspecialties",
            joinColumns = @JoinColumn(name = "doctor_id"),
            inverseJoinColumns = @JoinColumn(name = "subspecialty_id")
    )
    @JsonManagedReference("doctor-subspecialties")
    private Set<SubSpecialty> subSpecialties = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<DoctorSchedule> schedules = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<DoctorUnavailability> unavailabilities = new HashSet<>();

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(unique = true, nullable = false, length = 150)
    private String email;

    @Column(name = "license_number", unique = true, nullable = false, length = 50)
    private String licenseNumber;

    @Column(length = 200)
    private String address;

    @Column(name = "hourly_rate", precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Activa el doctor
     */
    public void activate() {
        this.active = true;
    }

    /**
     * Desactiva el doctor
     */
    public void deactivate() {
        this.active = false;
    }

    /**
     * Obtiene el nombre completo del doctor
     */
    public String getFullName() {
        return name + " " + lastName;
    }

    /**
     * Verifica si el doctor está disponible en un día y hora específicos
     */
    public boolean isAvailableAt(DayOfWeek dayOfWeek, LocalTime time, LocalDate date) {
        // 1. Verificar si está activo
        if (!active) {
            return false;
        }

        // 2. Verificar si tiene ausencias aprobadas en esa fecha
        boolean hasUnavailability = unavailabilities.stream()
                .anyMatch(u -> u.isActiveOn(date));

        if (hasUnavailability) {
            return false;
        }

        // 3. Verificar si tiene un horario activo para ese día y hora
        return schedules.stream()
                .anyMatch(s -> s.isAvailableAt(dayOfWeek, time));
    }

    /**
     * Verifica si el doctor tiene algún horario configurado
     */
    public boolean hasSchedules() {
        return schedules != null && !schedules.isEmpty();
    }

    /**
     * Verifica si el doctor tiene una especialidad específica
     */
    public boolean hasSpecialty(Long specialtyId) {
        return specialties.stream()
                .anyMatch(s -> s.getId().equals(specialtyId));
    }

    /**
     * Verifica si el doctor tiene una subespecialidad específica
     */
    public boolean hasSubSpecialty(Long subSpecialtyId) {
        return subSpecialties.stream()
                .anyMatch(ss -> ss.getId().equals(subSpecialtyId));
    }

    /**
     * Agrega un horario de atención
     */
    public void addSchedule(DoctorSchedule schedule) {
        schedules.add(schedule);
        schedule.setDoctor(this);
    }

    /**
     * Remueve un horario de atención
     */
    public void removeSchedule(DoctorSchedule schedule) {
        schedules.remove(schedule);
        schedule.setDoctor(null);
    }

    /**
     * Agrega una no disponibilidad
     */
    public void addUnavailability(DoctorUnavailability unavailability) {
        unavailabilities.add(unavailability);
        unavailability.setDoctor(this);
    }

    /**
     * Remueve una no disponibilidad
     */
    public void removeUnavailability(DoctorUnavailability unavailability) {
        unavailabilities.remove(unavailability);
        unavailability.setDoctor(null);
    }
}
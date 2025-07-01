package com.ClinicaDeYmid.suppliers_service.module.entity;

import com.ClinicaDeYmid.suppliers_service.module.dto.AttentionGetDto;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "doctors", indexes = {
        @Index(name = "idx_doctor_license", columnList = "license_number"),
        @Index(name = "idx_doctor_email", columnList = "email"),
        @Index(name = "idx_doctor_active", columnList = "active")
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
    private int providerCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "identification_number", nullable = false, unique = true)
    private String identificationNumber;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "doctor_subspecialties",
            joinColumns = @JoinColumn(name = "doctor_id"),
            inverseJoinColumns = @JoinColumn(name = "subspecialty_id")
    )
    private List<SubSpecialty> subSpecialties = new ArrayList<>();

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "license_number", unique = true, nullable = false)
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

    @Transient
    private List<AttentionGetDto> attentions;

    // Métodos de utilidad
    public boolean isActive() {
        return active != null && active;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public String getFullName() {
        return name + " " + lastName;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (active == null) {
            active = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "Doctor{" +
                "id=" + id +
                ", name='" + getFullName() + '\'' +
                ", email='" + email + '\'' +
                ", active=" + active +
                '}';
    }
}

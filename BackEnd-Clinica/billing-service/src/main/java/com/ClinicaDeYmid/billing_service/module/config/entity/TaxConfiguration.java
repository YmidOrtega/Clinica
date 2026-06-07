package com.ClinicaDeYmid.billing_service.module.config.entity;

import com.ClinicaDeYmid.billing_service.module.config.enums.TaxType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tax_configuration",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tax_configuration_code", columnNames = "code")
        },
        indexes = {
                @Index(name = "idx_tax_configuration_type", columnList = "type"),
                @Index(name = "idx_tax_configuration_active", columnList = "active")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class TaxConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TaxType type;

    @Column(name = "percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    @Column(name = "applies_to_services", nullable = false)
    @Builder.Default
    private Boolean appliesToServices = true;

    @Column(name = "applies_to_medications", nullable = false)
    @Builder.Default
    private Boolean appliesToMedications = false;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

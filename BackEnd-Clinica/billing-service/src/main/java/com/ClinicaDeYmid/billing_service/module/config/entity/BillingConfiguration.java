package com.ClinicaDeYmid.billing_service.module.config.entity;

import com.ClinicaDeYmid.billing_service.module.config.enums.DianEnvironment;
import com.ClinicaDeYmid.billing_service.module.config.enums.TaxRegime;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "billing_configuration",
        indexes = {
                @Index(name = "idx_billing_configuration_active", columnList = "active"),
                @Index(name = "idx_billing_configuration_dian_environment", columnList = "dian_environment")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class BillingConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clinic_nit", nullable = false, length = 20)
    private String clinicNit;

    @Column(name = "social_reason", nullable = false, length = 300)
    private String socialReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_regime", nullable = false, length = 30)
    private TaxRegime taxRegime;

    @Enumerated(EnumType.STRING)
    @Column(name = "dian_environment", nullable = false, length = 20)
    @Builder.Default
    private DianEnvironment dianEnvironment = DianEnvironment.HABILITACION;

    @Column(name = "software_id", length = 100)
    private String softwareId;

    @Column(name = "software_pin", length = 100)
    private String softwarePin;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;
}

package com.ClinicaDeYmid.suppliers_service.module.entity;

import com.ClinicaDeYmid.suppliers_service.module.domain.Nit;
import com.ClinicaDeYmid.suppliers_service.module.enums.Status;
import com.ClinicaDeYmid.suppliers_service.module.enums.TypeProvider;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;


@Entity
@Table(name = "health_providers",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_health_policies_nit", columnNames = "nit"),
                @UniqueConstraint(name = "uk_health_policies_number_contract", columnNames = "number_contract")
        },
        indexes = {
                @Index(name = "idx_health_policies_social_reason", columnList = "social_reason"),
                @Index(name = "idx_health_policies_type", columnList = "type"),
                @Index(name = "idx_health_policies_active", columnList = "active"),
                @Index(name = "idx_health_policies_year_validity", columnList = "year_of_validity"),
                @Index(name = "idx_health_policies_created_at", columnList = "created_at")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class HealthProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "social_reason", nullable = false, length = 200)
    private String socialReason;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name="value", column=@Column(name="nit", nullable = false, length = 20))
    })
    private Nit nit;

    @Column(name = "contract", length = 100)
    private String contract;

    @Column(name = "number_contract", length = 100)
    private String numberContract;

    @Column(name = "type_provider", length = 50)
    private TypeProvider typeProvider;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "phone", length = 20)
    private String phone;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "year_of_validity")
    private Integer yearOfValidity;

    @Column(name = "year_completion")
    private Integer yearCompletion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Override
    public String toString() {
        return "HealthProvider{" +
                "id=" + id +
                ", socialReason='" + socialReason + '\'' +
                ", type='" + typeProvider + '\'' +
                ", active=" + active +
                ", yearOfValidity=" + yearOfValidity +
                ", yearCompletion=" + yearCompletion +
                '}';
    }
}




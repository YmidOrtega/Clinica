package com.ClinicaDeYmid.clients_service.module.entity;

import com.ClinicaDeYmid.clients_service.module.domain.Nit;
import com.ClinicaDeYmid.clients_service.module.enums.TypeProvider;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "health_providers",
        uniqueConstraints = {

        },
        indexes = {
                @Index(name = "idx_health_providers_social_reason", columnList = "social_reason"),
                @Index(name = "idx_health_providers_type_provider", columnList = "type_provider"),
                @Index(name = "idx_health_providers_active", columnList = "active"),
                @Index(name = "idx_health_providers_year_of_validity", columnList = "year_of_validity"),
                @Index(name = "idx_health_providers_created_at", columnList = "created_at")
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
            @AttributeOverride(name = "value", column = @Column(name = "nit", nullable = false, length = 20))
    })
    private Nit nit;

    @Enumerated(EnumType.STRING)
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

    @OneToMany(mappedBy = "healthProvider", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @Builder.Default
    private List<Contract> contracts = new ArrayList<>();

    @Override
    public String toString() {
        return "HealthProvider{" +
                "id=" + id +
                ", socialReason='" + socialReason + '\'' +
                ", typeProvider='" + typeProvider + '\'' +
                ", active=" + active +
                ", yearOfValidity=" + yearOfValidity +
                ", yearCompletion=" + yearCompletion +
                '}';
    }
}
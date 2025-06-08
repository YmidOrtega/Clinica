package com.ClinicaDeYmid.clients_service.module.entity;

import com.ClinicaDeYmid.clients_service.module.enums.ContractStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "contracts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_contracts_contract_number", columnNames = "contract_number")
        },
        indexes = {
                @Index(name = "idx_contracts_health_provider_id", columnList = "health_provider_id"),
                @Index(name = "idx_contracts_status", columnList = "status"),
                @Index(name = "idx_contracts_start_date", columnList = "start_date"),
                @Index(name = "idx_contracts_end_date", columnList = "end_date"),
                @Index(name = "idx_contracts_created_at", columnList = "created_at")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "health_provider_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_contracts_health_provider"))
    private HealthProvider healthProvider;

    @Column(name = "contract_number", nullable = false, length = 100)
    private String contractNumber;

    @Column(name = "agreed_tariff", precision = 15, scale = 2)
    private BigDecimal agreedTariff;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private ContractStatus status = ContractStatus.ACTIVE;


    @Column(name = "service_name", length = 200)
    @Builder.Default
    private List<Portfolio> coveredServices = new ArrayList<>();

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Override
    public String toString() {
        return "Contract{" +
                "id=" + id +
                ", contractNumber='" + contractNumber + '\'' +
                ", agreedTariff=" + agreedTariff +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", status=" + status +
                ", active=" + active +
                '}';
    }
}
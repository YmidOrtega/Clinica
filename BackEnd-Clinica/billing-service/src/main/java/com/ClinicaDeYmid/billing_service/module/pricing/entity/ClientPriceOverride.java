package com.ClinicaDeYmid.billing_service.module.pricing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "client_price_override",
        indexes = {
                @Index(name = "idx_client_price_override_contract_id", columnList = "contract_id"),
                @Index(name = "idx_client_price_override_portfolio_id", columnList = "portfolio_id"),
                @Index(name = "idx_client_price_override_health_provider_nit", columnList = "health_provider_nit"),
                @Index(name = "idx_client_price_override_active", columnList = "active"),
                @Index(name = "idx_client_price_override_lookup", columnList = "contract_id, portfolio_id, active")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class ClientPriceOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "health_provider_nit", nullable = false, length = 20)
    private String healthProviderNit;

    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;

    @Column(name = "code_cups", length = 50)
    private String codeCups;

    @Column(name = "negotiated_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal negotiatedPrice;

    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;

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

    public boolean isCurrentlyValid() {
        LocalDate today = LocalDate.now();
        return active && !today.isBefore(validFrom) && !today.isAfter(validTo);
    }
}

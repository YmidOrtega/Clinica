package com.ClinicaDeYmid.billing_service.module.pricing.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "price_manual_item",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_price_manual_item_manual_portfolio",
                        columnNames = {"price_manual_id", "portfolio_id"})
        },
        indexes = {
                @Index(name = "idx_price_manual_item_manual_id", columnList = "price_manual_id"),
                @Index(name = "idx_price_manual_item_portfolio_id", columnList = "portfolio_id"),
                @Index(name = "idx_price_manual_item_code_cups", columnList = "code_cups"),
                @Index(name = "idx_price_manual_item_active", columnList = "active")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class PriceManualItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_manual_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_price_manual_item_manual"))
    @ToString.Exclude
    private PriceManual priceManual;

    @Column(name = "portfolio_id")
    private Long portfolioId;

    @Column(name = "code_cups", length = 50)
    private String codeCups;

    @Column(name = "code_clinic", length = 50)
    private String codeClinic;

    @Column(name = "description", nullable = false, length = 300)
    private String description;

    @Column(name = "base_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
}

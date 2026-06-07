package com.ClinicaDeYmid.billing_service.module.sale.entity;

import com.ClinicaDeYmid.billing_service.module.sale.enums.SaleItemType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "sale_order_item",
        indexes = {
                @Index(name = "idx_sale_order_item_sale_order_id", columnList = "sale_order_id"),
                @Index(name = "idx_sale_order_item_portfolio_id", columnList = "portfolio_id"),
                @Index(name = "idx_sale_order_item_type", columnList = "item_type"),
                @Index(name = "idx_sale_order_item_code_cups", columnList = "code_cups")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class SaleOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_order_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_sale_order_item_sale_order"))
    private SaleOrder saleOrder;

    @Column(name = "portfolio_id")
    private Long portfolioId;

    @Column(name = "code_cups", length = 50)
    private String codeCups;

    @Column(name = "code_clinic", length = 50)
    private String codeClinic;

    @Column(name = "description", nullable = false, length = 300)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private SaleItemType itemType;

    @Column(name = "quantity", nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(name = "subtotal", nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "authorized", nullable = false)
    @Builder.Default
    private Boolean authorized = false;

    @Column(name = "authorization_id")
    private Long authorizationId;

    public void calculateSubtotal() {
        BigDecimal base = unitPrice.multiply(BigDecimal.valueOf(quantity));
        if (discountPercentage != null && discountPercentage.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal factor = BigDecimal.ONE
                    .subtract(discountPercentage.divide(BigDecimal.valueOf(100)));
            base = base.multiply(factor).setScale(2, RoundingMode.HALF_UP);
        }
        this.subtotal = base.setScale(2, RoundingMode.HALF_UP);
    }
}

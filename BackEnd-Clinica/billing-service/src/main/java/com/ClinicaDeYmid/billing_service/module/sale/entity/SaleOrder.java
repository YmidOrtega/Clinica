package com.ClinicaDeYmid.billing_service.module.sale.entity;

import com.ClinicaDeYmid.billing_service.module.sale.enums.SaleOrderStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sale_order",
        indexes = {
                @Index(name = "idx_sale_order_attention_id", columnList = "attention_id"),
                @Index(name = "idx_sale_order_patient_id", columnList = "patient_id"),
                @Index(name = "idx_sale_order_health_provider_nit", columnList = "health_provider_nit"),
                @Index(name = "idx_sale_order_status", columnList = "status"),
                @Index(name = "idx_sale_order_attention_status", columnList = "attention_id, status")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class SaleOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attention_id", nullable = false)
    private Long attentionId;

    @Column(name = "patient_id", nullable = false, length = 20)
    private String patientId;

    @Column(name = "health_provider_nit", nullable = false, length = 20)
    private String healthProviderNit;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "doctor_id")
    private Long doctorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SaleOrderStatus status = SaleOrderStatus.DRAFT;

    @Column(name = "subtotal", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "copayment_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal copaymentAmount = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Column(name = "notes", length = 500)
    private String notes;

    @OneToMany(mappedBy = "saleOrder", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    private List<SaleOrderItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    public void recalculateTotals() {
        this.subtotal = items.stream()
                .filter(i -> i.getSubtotal() != null)
                .map(SaleOrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.taxAmount = items.stream()
                .filter(i -> i.getTaxRate() != null && i.getSubtotal() != null)
                .map(i -> i.getSubtotal().multiply(i.getTaxRate())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.totalAmount = this.subtotal.add(this.taxAmount);
        this.netAmount = this.totalAmount.subtract(this.copaymentAmount);
    }

    public boolean isDraft() {
        return SaleOrderStatus.DRAFT == this.status;
    }

    public boolean canBeConfirmed() {
        return isDraft() && !items.isEmpty();
    }

    public boolean canBeCancelled() {
        return this.status != SaleOrderStatus.INVOICED;
    }
}

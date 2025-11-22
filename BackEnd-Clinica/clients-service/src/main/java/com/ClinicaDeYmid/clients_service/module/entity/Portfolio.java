package com.ClinicaDeYmid.clients_service.module.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "portfolios",
        indexes = {
                @Index(name = "idx_portfolios_code_cups", columnList = "code_cups"),
                @Index(name = "idx_portfolios_code_clinic", columnList = "code_clinic"),
                @Index(name = "idx_portfolios_name", columnList = "name")
        })
@SQLDelete(sql = "UPDATE portfolios SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "code_cups", nullable = false)
    private String codeCups;

    @Column(name = "code_clinic", nullable = false)
    private String codeClinic;

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    @ManyToMany(mappedBy = "coveredServices", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Contract> contracts = new ArrayList<>();

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "deletion_reason", length = 500)
    private String deletionReason;

    /**
     * Verifica si el portfolio está eliminado (soft delete)
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Marca el portfolio como eliminado con información de auditoría
     */
    public void markAsDeleted(Long userId, String reason) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = userId;
        this.deletionReason = reason;
    }

    /**
     * Restaura un portfolio eliminado
     */
    public void restore() {
        this.deletedAt = null;
        this.deletedBy = null;
        this.deletionReason = null;
    }

    @Override
    public String toString() {
        return "Portfolio{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", codeCups='" + codeCups + '\'' +
                ", codeClinic='" + codeClinic + '\'' +
                ", price=" + price +
                ", deleted=" + isDeleted() +
                '}';
    }
}
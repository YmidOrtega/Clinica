package com.ClinicaDeYmid.admissions_service.module.entity;

import com.ClinicaDeYmid.admissions_service.module.enums.TypeOfAuthorization;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "authorizations")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE authorizations SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Authorization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attention_id", nullable = false)
    private Attention attention;

    @Column(name = "authorization_number", nullable = false)
    private String authorizationNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_of_authorization", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TypeOfAuthorization typeOfAuthorization;

    @Column(name = "authorization_by", nullable = false)
    private String authorizationBy;

    @Column(name = "copayment_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal copaymentValue;

    @ElementCollection
    @CollectionTable(name = "authorization_portfolio_items", joinColumns = @JoinColumn(name = "authorization_id"))
    @Column(name = "portfolio_item_id")
    private List<Long> authorizedPortfolioIds;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "deletion_reason", length = 500)
    private String deletionReason;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;

    public void softDelete(Long userId, String reason) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = userId;
        this.deletionReason = reason;
    }

    public void restore() {
        this.deletedAt = null;
        this.deletedBy = null;
        this.deletionReason = null;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

}

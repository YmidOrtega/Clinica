package com.ClinicaDeYmid.billing_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patient_share_adjustments")
@EntityListeners(AuditingEntityListener.class)
public class PatientShareAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, unique = true, length = 36)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private EpisodeAccount account;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sale_uuid", updatable = false, length = 36)
    private UUID saleUuid;

    @Column(name = "amount", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", nullable = false, updatable = false, length = 500)
    private String reason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 36)
    private String createdBy;

    protected PatientShareAdjustment() {
    }

    public static PatientShareAdjustment of(EpisodeAccount account, UUID saleUuid, BigDecimal amount, String reason) {
        PatientShareAdjustment adjustment = new PatientShareAdjustment();
        adjustment.uuid = UUID.randomUUID();
        adjustment.account = DomainRules.required(account, "account");
        adjustment.saleUuid = saleUuid;
        DomainRules.required(amount, "amount");
        if (amount.signum() < 0 || amount.stripTrailingZeros().scale() > 2 || amount.compareTo(Money.MAXIMUM) > 0) {
            throw new BillingException.InvalidData("amount", "debe ser un valor en pesos no negativo con hasta dos decimales");
        }
        adjustment.amount = Money.of(amount);
        adjustment.reason = DomainRules.requiredText(reason, "reason", 500);
        return adjustment;
    }

    public UUID uuid() {
        return uuid;
    }

    public UUID saleUuid() {
        return saleUuid;
    }

    public BigDecimal amount() {
        return amount;
    }

    public String reason() {
        return reason;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String createdBy() {
        return createdBy;
    }
}

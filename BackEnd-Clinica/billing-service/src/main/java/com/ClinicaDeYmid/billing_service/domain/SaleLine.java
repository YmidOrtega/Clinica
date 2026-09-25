package com.ClinicaDeYmid.billing_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "sale_lines")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class SaleLine {

    static final int MAXIMUM_QUANTITY = 999;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, unique = true, length = 36)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, updatable = false)
    private Sale sale;

    @Column(name = "position", nullable = false, updatable = false)
    private int position;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "portfolio_item_uuid", nullable = false, updatable = false, length = 36)
    private UUID portfolioItemUuid;

    @Column(name = "cups_code", nullable = false, updatable = false, length = 8)
    private String cupsCode;

    @Column(name = "clinic_code", updatable = false, length = 20)
    private String clinicCode;

    @Column(name = "description", nullable = false, updatable = false, length = 300)
    private String description;

    @Column(name = "category", updatable = false, length = 40)
    private String category;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    @Column(name = "service_date", nullable = false, updatable = false)
    private LocalDate serviceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, updatable = false, length = 20)
    private LineOrigin.Code originCode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "authorization_uuid", updatable = false, length = 36)
    private UUID authorizationUuid;

    @Column(name = "authorization_number", updatable = false, length = 40)
    private String authorizationNumber;

    @Column(name = "manual_unit_price", precision = 14, scale = 2)
    private BigDecimal manualUnitPrice;

    @Column(name = "manual_price_reason", length = 500)
    private String manualPriceReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_origin", length = 20)
    private PriceOrigin priceOrigin;

    @Column(name = "unit_price", precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", precision = 14, scale = 2)
    private BigDecimal lineTotal;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "price_reference_uuid", length = 36)
    private UUID priceReferenceUuid;

    @Column(name = "price_reference_code", length = 40)
    private String priceReferenceCode;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removal_reason", length = 500)
    private String removalReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 36)
    private String createdBy;

    protected SaleLine() {
    }

    static SaleLine charge(Sale sale, int position, ChargedService service, int quantity, LocalDate serviceDate,
                           LineOrigin origin) {
        if (quantity < 1 || quantity > MAXIMUM_QUANTITY) {
            throw new BillingException.InvalidData("quantity", "debe estar entre 1 y " + MAXIMUM_QUANTITY);
        }
        SaleLine line = new SaleLine();
        line.uuid = UUID.randomUUID();
        line.sale = sale;
        line.position = position;
        line.portfolioItemUuid = service.portfolioItemUuid();
        line.cupsCode = service.cupsCode();
        line.clinicCode = service.clinicCode();
        line.description = service.description();
        line.category = service.category();
        line.quantity = quantity;
        line.serviceDate = DomainRules.required(serviceDate, "serviceDate");
        line.originCode = DomainRules.required(origin, "origin").code();
        if (origin instanceof LineOrigin.Authorized authorized) {
            line.authorizationUuid = authorized.authorizationUuid();
            line.authorizationNumber = authorized.authorizationNumber();
        }
        return line;
    }

    void remove(String reason, Instant now) {
        if (removed()) {
            throw new BillingException.LineAlreadyRemoved();
        }
        removalReason = DomainRules.requiredText(reason, "reason", 500);
        removedAt = now;
    }

    void setManualPrice(BigDecimal unitPrice, String reason) {
        if (removed()) {
            throw new BillingException.LineAlreadyRemoved();
        }
        manualUnitPrice = Money.positive(unitPrice, "unitPrice");
        manualPriceReason = DomainRules.requiredText(reason, "reason", 500);
    }

    LinePrice priceFrom(LinePrice quoted) {
        LinePrice found = quoted == null ? LinePrice.unpriced() : quoted;
        if (found.pending() && manualUnitPrice != null) {
            return LinePrice.manual(manualUnitPrice, quantity);
        }
        return found;
    }

    void settle(LinePrice price) {
        priceOrigin = price.origin();
        unitPrice = price.unitPrice();
        lineTotal = price.lineTotal();
        priceReferenceUuid = price.referenceUuid();
        priceReferenceCode = price.referenceCode();
    }

    public Optional<LinePrice> price() {
        return priceOrigin == null ? Optional.empty()
                : Optional.of(new LinePrice(priceOrigin, unitPrice, lineTotal, priceReferenceUuid, priceReferenceCode));
    }

    public Optional<BigDecimal> manualUnitPrice() {
        return Optional.ofNullable(manualUnitPrice);
    }

    public String manualPriceReason() {
        return manualPriceReason;
    }

    public boolean removed() {
        return removedAt != null;
    }

    public UUID uuid() {
        return uuid;
    }

    public int position() {
        return position;
    }

    public ChargedService service() {
        return new ChargedService(portfolioItemUuid, cupsCode, clinicCode, description, category);
    }

    public int quantity() {
        return quantity;
    }

    public LocalDate serviceDate() {
        return serviceDate;
    }

    public LineOrigin origin() {
        return switch (originCode) {
            case MANUAL -> new LineOrigin.Manual();
            case AUTHORIZED -> new LineOrigin.Authorized(authorizationUuid, authorizationNumber);
        };
    }

    public Instant removedAt() {
        return removedAt;
    }

    public String removalReason() {
        return removalReason;
    }
}

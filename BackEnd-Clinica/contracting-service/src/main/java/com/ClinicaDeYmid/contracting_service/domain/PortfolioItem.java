package com.ClinicaDeYmid.contracting_service.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "portfolio_items")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class PortfolioItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, unique = true, length = 36)
    private UUID uuid;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Embedded
    @AttributeOverride(name = "cups", column = @Column(name = "cups_code", nullable = false, length = 8))
    @AttributeOverride(name = "clinic", column = @Column(name = "clinic_code", nullable = false, length = 20))
    private ServiceCode code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private ServiceCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PortfolioItemStatus.Code statusCode;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 36)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 36)
    private String updatedBy;

    protected PortfolioItem() {
    }

    public static PortfolioItem offer(ServiceCode code, String name, ServiceCategory category) {
        PortfolioItem item = new PortfolioItem();
        item.uuid = UUID.randomUUID();
        item.code = DomainRules.required(code, "code");
        item.name = DomainRules.requiredText(name, "name", 200);
        item.category = DomainRules.required(category, "category");
        item.statusCode = PortfolioItemStatus.Code.ACTIVE;
        return item;
    }

    public boolean describe(ServiceCode newCode, String newName, ServiceCategory newCategory) {
        ServiceCode code = DomainRules.required(newCode, "code");
        String name = DomainRules.requiredText(newName, "name", 200);
        ServiceCategory category = DomainRules.required(newCategory, "category");
        if (code.equals(this.code) && name.equals(this.name) && category == this.category) {
            return false;
        }
        this.code = code;
        this.name = name;
        this.category = category;
        return true;
    }

    public void stopOffering(String reason, Clock clock) {
        applyStatus(status().deactivate(reason, Instant.now(clock)));
    }

    public void offerAgain() {
        applyStatus(status().reactivate());
    }

    public PortfolioItemStatus status() {
        return switch (statusCode) {
            case ACTIVE -> new PortfolioItemStatus.Active();
            case INACTIVE -> new PortfolioItemStatus.Inactive(statusReason, statusChangedAt);
        };
    }

    public UUID uuid() {
        return uuid;
    }

    public long version() {
        return version;
    }

    public ServiceCode code() {
        return code;
    }

    public String name() {
        return name;
    }

    public ServiceCategory category() {
        return category;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private void applyStatus(PortfolioItemStatus status) {
        statusCode = status.code();
        statusReason = status instanceof PortfolioItemStatus.Inactive inactive ? inactive.reason() : null;
        statusChangedAt = status instanceof PortfolioItemStatus.Inactive inactive ? inactive.since() : null;
    }
}

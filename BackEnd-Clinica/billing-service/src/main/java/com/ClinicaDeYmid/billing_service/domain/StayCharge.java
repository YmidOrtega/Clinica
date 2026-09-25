package com.ClinicaDeYmid.billing_service.domain;

import jakarta.persistence.Column;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stay_charges")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class StayCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "stay_type", nullable = false, updatable = false, unique = true, length = 30)
    private StayType stayType;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "portfolio_item_uuid", nullable = false, length = 36)
    private UUID portfolioItemUuid;

    @Column(name = "cups_code", nullable = false, length = 8)
    private String cupsCode;

    @Column(name = "clinic_code", length = 20)
    private String clinicCode;

    @Column(name = "description", nullable = false, length = 300)
    private String description;

    @Column(name = "category", length = 40)
    private String category;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 36)
    private String updatedBy;

    protected StayCharge() {
    }

    public static StayCharge of(StayType stayType, ChargedService service) {
        StayCharge charge = new StayCharge();
        charge.stayType = DomainRules.required(stayType, "stayType");
        charge.bill(service);
        return charge;
    }

    public void bill(ChargedService service) {
        DomainRules.required(service, "service");
        portfolioItemUuid = service.portfolioItemUuid();
        cupsCode = service.cupsCode();
        clinicCode = service.clinicCode();
        description = service.description();
        category = service.category();
    }

    public StayType stayType() {
        return stayType;
    }

    public ChargedService service() {
        return new ChargedService(portfolioItemUuid, cupsCode, clinicCode, description, category);
    }

    public long version() {
        return version;
    }
}

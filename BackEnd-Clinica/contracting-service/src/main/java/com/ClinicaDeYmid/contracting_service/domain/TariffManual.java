package com.ClinicaDeYmid.contracting_service.domain;

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
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "tariff_manuals")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class TariffManual {

    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{2,29}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, unique = true, length = 36)
    private UUID uuid;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false, updatable = false, length = 10)
    private PriceUnit unit;

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

    protected TariffManual() {
    }

    public static TariffManual of(String code, String name, PriceUnit unit) {
        TariffManual manual = new TariffManual();
        manual.uuid = UUID.randomUUID();
        manual.code = DomainRules.matching(DomainRules.upper(DomainRules.requiredText(code, "code", 30)), CODE, "code");
        manual.name = DomainRules.requiredText(name, "name", 200);
        manual.unit = DomainRules.required(unit, "unit");
        return manual;
    }

    public void rename(String newName) {
        name = DomainRules.requiredText(newName, "name", 200);
    }

    public UUID uuid() {
        return uuid;
    }

    public long version() {
        return version;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public PriceUnit unit() {
        return unit;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}

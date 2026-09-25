package com.ClinicaDeYmid.billing_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "sale_packages")
@Audited
public class SalePackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, updatable = false)
    private Sale sale;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "package_uuid", nullable = false, updatable = false, length = 36)
    private UUID packageUuid;

    @Column(name = "code", nullable = false, updatable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, updatable = false, length = 200)
    private String name;

    @Column(name = "price", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal price;

    protected SalePackage() {
    }

    static SalePackage of(Sale sale, PackageCharge charge) {
        SalePackage applied = new SalePackage();
        applied.sale = sale;
        applied.packageUuid = charge.packageUuid();
        applied.code = charge.code();
        applied.name = charge.name();
        applied.price = charge.price();
        return applied;
    }

    public PackageCharge charge() {
        return new PackageCharge(packageUuid, code, name, price);
    }
}

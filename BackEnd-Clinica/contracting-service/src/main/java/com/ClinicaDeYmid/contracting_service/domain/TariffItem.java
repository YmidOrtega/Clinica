package com.ClinicaDeYmid.contracting_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.regex.Pattern;

@Entity
@Table(name = "tariff_items")
public class TariffItem {

    private static final Pattern CUPS = Pattern.compile("^[0-9]{6,8}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manual_version_id", nullable = false, updatable = false)
    private TariffManualVersion manualVersion;

    @Column(name = "cups_code", nullable = false, updatable = false, length = 8)
    private String cupsCode;

    @Column(name = "description", nullable = false, updatable = false, length = 300)
    private String description;

    @Column(name = "value", nullable = false, updatable = false, precision = 15, scale = 4)
    private BigDecimal value;

    protected TariffItem() {
    }

    public static TariffItem of(TariffManualVersion manualVersion, String cupsCode, String description, BigDecimal value) {
        TariffItem item = new TariffItem();
        item.manualVersion = DomainRules.required(manualVersion, "manualVersion");
        manualVersion.requireEditable();
        item.cupsCode = DomainRules.matching(DomainRules.requiredText(cupsCode, "cupsCode", 8), CUPS, "cupsCode");
        item.description = DomainRules.requiredText(description, "description", 300);
        item.value = DomainRules.required(value, "value");
        if (item.value.signum() < 0) {
            throw new ContractingException.InvalidData("value", "no puede ser negativo");
        }
        return item;
    }

    public TariffManualVersion manualVersion() {
        return manualVersion;
    }

    public String cupsCode() {
        return cupsCode;
    }

    public String description() {
        return description;
    }

    public BigDecimal value() {
        return value;
    }

    public BigDecimal inPesos() {
        return manualVersion.inPesos(value);
    }
}

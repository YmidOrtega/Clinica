package com.ClinicaDeYmid.contracting_service.domain;

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
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "tariff_manual_versions")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class TariffManualVersion {

    private static final Pattern LABEL = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 ._-]{0,29}$");
    private static final Pattern CHECKSUM = Pattern.compile("^[0-9a-f]{64}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, unique = true, length = 36)
    private UUID uuid;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manual_id", nullable = false, updatable = false)
    private TariffManual manual;

    @Column(name = "label", nullable = false, updatable = false, length = 30)
    private String label;

    @Column(name = "unit_value", nullable = false, precision = 15, scale = 4)
    private BigDecimal unitValue;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TariffVersionStatus.Code statusCode;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "source_checksum", length = 64)
    private String sourceChecksum;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

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

    protected TariffManualVersion() {
    }

    public static TariffManualVersion draft(TariffManual manual, String label, BigDecimal unitValue, LocalDate validFrom) {
        TariffManualVersion version = new TariffManualVersion();
        version.uuid = UUID.randomUUID();
        version.manual = DomainRules.required(manual, "manual");
        version.label = DomainRules.matching(DomainRules.requiredText(label, "label", 30), LABEL, "label");
        version.unitValue = requireUnitValue(manual.unit(), unitValue);
        version.validFrom = DomainRules.required(validFrom, "validFrom");
        version.statusCode = TariffVersionStatus.Code.DRAFT;
        version.itemCount = 0;
        return version;
    }

    public void loaded(String checksum, int loadedItems) {
        requireEditable();
        sourceChecksum = DomainRules.matching(DomainRules.lower(DomainRules.requiredText(checksum, "checksum", 64)),
                CHECKSUM, "checksum");
        if (loadedItems <= 0) {
            throw new ContractingException.InvalidData("items", "debe traer al menos una tarifa");
        }
        itemCount = loadedItems;
    }

    public void activate(Clock clock) {
        if (itemCount == 0) {
            throw new ContractingException.TariffVersionNotEditable("no tiene tarifas cargadas");
        }
        applyStatus(status().activate(Instant.now(clock)));
    }

    public void retire(Clock clock) {
        applyStatus(status().retire(Instant.now(clock)));
    }

    public BigDecimal inPesos(BigDecimal value) {
        return manual.unit().toPesos(value, unitValue);
    }

    public TariffVersionStatus status() {
        return switch (statusCode) {
            case DRAFT -> new TariffVersionStatus.Draft();
            case ACTIVE -> new TariffVersionStatus.Active(statusChangedAt);
            case RETIRED -> new TariffVersionStatus.Retired(statusChangedAt);
        };
    }

    public void requireEditable() {
        if (!status().editable()) {
            throw new ContractingException.TariffVersionNotEditable("ya fue publicada");
        }
    }

    public UUID uuid() {
        return uuid;
    }

    public long version() {
        return version;
    }

    public TariffManual manual() {
        return manual;
    }

    public String label() {
        return label;
    }

    public BigDecimal unitValue() {
        return unitValue;
    }

    public LocalDate validFrom() {
        return validFrom;
    }

    public String sourceChecksum() {
        return sourceChecksum;
    }

    public int itemCount() {
        return itemCount;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private void applyStatus(TariffVersionStatus status) {
        statusCode = status.code();
        statusChangedAt = switch (status) {
            case TariffVersionStatus.Draft draft -> null;
            case TariffVersionStatus.Active active -> active.since();
            case TariffVersionStatus.Retired retired -> retired.since();
        };
    }

    private static BigDecimal requireUnitValue(PriceUnit unit, BigDecimal unitValue) {
        BigDecimal value = DomainRules.required(unitValue, "unitValue");
        if (value.signum() <= 0) {
            throw new ContractingException.InvalidData("unitValue", "debe ser mayor que cero");
        }
        if (unit.fixedInPesos() && value.compareTo(BigDecimal.ONE) != 0) {
            throw new ContractingException.InvalidData("unitValue", "debe ser 1 cuando el manual está en pesos");
        }
        return value;
    }
}

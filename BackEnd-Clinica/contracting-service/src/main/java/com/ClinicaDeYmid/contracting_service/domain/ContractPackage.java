package com.ClinicaDeYmid.contracting_service.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "contract_packages")
public class ContractPackage {

    private static final Pattern CUPS = Pattern.compile("^[0-9]{6,8}$");
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9][A-Z0-9.-]{1,19}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, unique = true, length = 36)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false, updatable = false)
    private Contract contract;

    @Column(name = "code", nullable = false, updatable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, updatable = false, length = 200)
    private String name;

    @Column(name = "price", nullable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "contract_package_items", joinColumns = @JoinColumn(name = "package_id"))
    @Column(name = "cups_code", nullable = false, length = 8)
    private Set<String> includedCodes = new LinkedHashSet<>();

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;

    @Column(name = "revoked_from")
    private LocalDate revokedFrom;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    @Column(name = "registered_by", updatable = false, length = 36)
    private String registeredBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by", length = 36)
    private String revokedBy;

    protected ContractPackage() {
    }

    public static ContractPackage agree(Contract contract, String code, String name, BigDecimal price,
                                        Set<String> includedCodes, LocalDate validFrom, String registeredBy, Clock clock) {
        ContractPackage agreed = new ContractPackage();
        agreed.uuid = UUID.randomUUID();
        agreed.contract = DomainRules.required(contract, "contract");
        if (contract.modality() != ContractModality.PACKAGE && contract.modality() != ContractModality.EVENT) {
            throw new ContractingException.PackagesNotApplicable(contract.modality());
        }
        agreed.code = DomainRules.matching(DomainRules.upper(DomainRules.requiredText(code, "code", 20)), CODE, "code");
        agreed.name = DomainRules.requiredText(name, "name", 200);
        agreed.price = requirePrice(price);
        agreed.includedCodes = requireCodes(includedCodes);
        agreed.validFrom = DomainRules.required(validFrom, "validFrom");
        if (validFrom.isBefore(contract.validFrom())) {
            throw new ContractingException.InvalidData("validFrom", "no puede empezar antes que el contrato");
        }
        agreed.registeredAt = Instant.now(clock);
        agreed.registeredBy = registeredBy;
        return agreed;
    }

    public void revoke(LocalDate from, String actor, Clock clock) {
        if (revokedFrom != null) {
            throw new ContractingException.PackageAlreadyRevoked();
        }
        LocalDate end = DomainRules.required(from, "revokedFrom");
        if (end.isBefore(validFrom)) {
            throw new ContractingException.InvalidData("revokedFrom", "no puede ser anterior al inicio del paquete");
        }
        revokedFrom = end;
        revokedAt = Instant.now(clock);
        revokedBy = actor;
    }

    public boolean appliesOn(LocalDate date) {
        return !date.isBefore(validFrom) && (revokedFrom == null || date.isBefore(revokedFrom));
    }

    public boolean covers(String cupsCode) {
        return includedCodes.contains(cupsCode);
    }

    public UUID uuid() {
        return uuid;
    }

    public Contract contract() {
        return contract;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public BigDecimal price() {
        return price;
    }

    public Set<String> includedCodes() {
        return Set.copyOf(includedCodes);
    }

    public LocalDate validFrom() {
        return validFrom;
    }

    public LocalDate revokedFrom() {
        return revokedFrom;
    }

    private static BigDecimal requirePrice(BigDecimal price) {
        BigDecimal value = DomainRules.required(price, "price");
        if (value.signum() <= 0) {
            throw new ContractingException.InvalidData("price", "debe ser mayor que cero");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new ContractingException.InvalidData("price", "no puede tener más de dos decimales");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static Set<String> requireCodes(Set<String> codes) {
        Set<String> included = new LinkedHashSet<>(DomainRules.required(codes, "includedCodes"));
        if (included.size() < 2) {
            throw new ContractingException.InvalidData("includedCodes", "debe incluir al menos dos servicios");
        }
        included.forEach(code -> DomainRules.matching(code, CUPS, "includedCodes"));
        return included;
    }
}

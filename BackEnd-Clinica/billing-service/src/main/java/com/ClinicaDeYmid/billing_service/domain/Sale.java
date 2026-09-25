package com.ClinicaDeYmid.billing_service.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "sales")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class Sale {

    static final int MAXIMUM_SALES_PER_ACCOUNT = 99;
    static final int MAXIMUM_LINES = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, unique = true, length = 36)
    private UUID uuid;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private EpisodeAccount account;

    @Column(name = "sequence", nullable = false, updatable = false)
    private int sequence;

    @Column(name = "number", nullable = false, updatable = false, unique = true, length = 20)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 20)
    private SaleType.Code typeCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SaleStatus.Code statusCode;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "active_lines", nullable = false)
    private int activeLineCount;

    @Column(name = "edited_at")
    private Instant editedAt;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "contract_uuid", length = 36)
    private UUID contractUuid;

    @Column(name = "contract_number", length = 40)
    private String contractNumber;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "payer_uuid", length = 36)
    private UUID payerUuid;

    @Column(name = "lines_total", precision = 14, scale = 2)
    private BigDecimal linesTotal;

    @Column(name = "packages_total", precision = 14, scale = 2)
    private BigDecimal packagesTotal;

    @Column(name = "total", precision = 14, scale = 2)
    private BigDecimal total;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL)
    @OrderBy("code")
    private Set<SalePackage> packages = new LinkedHashSet<>();

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL)
    @OrderBy("position")
    private Set<SaleLine> lines = new LinkedHashSet<>();

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

    protected Sale() {
    }

    public static Sale open(EpisodeAccount account, int sequence, SaleType type) {
        DomainRules.required(account, "account");
        if (!account.status().acceptsCharges()) {
            throw new BillingException.AccountClosedForCharges(account.status().code());
        }
        if (sequence < 1 || sequence > MAXIMUM_SALES_PER_ACCOUNT) {
            throw new BillingException.TooManySales(MAXIMUM_SALES_PER_ACCOUNT);
        }
        Sale sale = new Sale();
        sale.uuid = UUID.randomUUID();
        sale.account = account;
        sale.sequence = sequence;
        sale.number = account.admissionNumber() + "-V%02d".formatted(sequence);
        sale.typeCode = DomainRules.required(type, "type").code();
        sale.statusCode = SaleStatus.Code.DRAFT;
        return sale;
    }

    public SaleLine charge(ChargedService service, int quantity, LocalDate serviceDate, LineOrigin origin,
                           Clock clock) {
        requireEditable();
        DomainRules.required(service, "service");
        DomainRules.required(serviceDate, "serviceDate");
        if (serviceDate.isAfter(LocalDate.now(clock))) {
            throw new BillingException.InvalidData("serviceDate", "no puede ser posterior a hoy");
        }
        if (serviceDate.isBefore(LocalDate.ofInstant(account.openedAt(), clock.getZone()))) {
            throw new BillingException.InvalidData("serviceDate", "no puede ser anterior al ingreso del episodio");
        }
        if (lines.size() >= MAXIMUM_LINES) {
            throw new BillingException.TooManyLines(MAXIMUM_LINES);
        }
        SaleLine line = SaleLine.charge(this, lines.size() + 1, service, quantity, serviceDate, origin);
        lines.add(line);
        activeLineCount++;
        editedAt = Instant.now(clock);
        return line;
    }

    public void removeLine(UUID lineUuid, String reason, Clock clock) {
        requireEditable();
        line(lineUuid).remove(reason, Instant.now(clock));
        activeLineCount--;
        editedAt = Instant.now(clock);
    }

    public void priceManually(UUID lineUuid, BigDecimal unitPrice, String reason, Clock clock) {
        requireEditable();
        line(lineUuid).setManualPrice(unitPrice, reason);
        editedAt = Instant.now(clock);
    }

    public PricedSale price(PricingTerms terms, ZoneId zone) {
        DomainRules.required(terms, "terms");
        Map<UUID, LinePrice> prices = new LinkedHashMap<>();
        Map<UUID, AuthorizationCheck> checks = new LinkedHashMap<>();
        List<SaleLine> pending = new ArrayList<>();
        List<SaleLine> unauthorized = new ArrayList<>();
        BigDecimal linesSum = Money.ZERO;
        for (SaleLine line : activeLines()) {
            LinePrice price = line.priceFrom(terms.quoted().get(line.uuid()));
            prices.put(line.uuid(), price);
            linesSum = linesSum.add(price.lineTotal());
            if (price.pending()) {
                pending.add(line);
            }
            AuthorizationCheck check = terms.authorizations()
                    .check(line, terms.requiringAuthorization().contains(line.uuid()), zone);
            checks.put(line.uuid(), check);
            if (check.blocks()) {
                unauthorized.add(line);
            }
        }
        BigDecimal packagesSum = terms.packages().stream().map(PackageCharge::price)
                .reduce(Money.ZERO, BigDecimal::add);
        return new PricedSale(terms, prices, pending, checks, unauthorized, Money.of(linesSum),
                Money.of(packagesSum), Money.of(linesSum.add(packagesSum)));
    }

    public void requireConfirmable() {
        if (!status().editable()) {
            throw new BillingException.InvalidSaleTransition(statusCode, SaleStatus.Code.CONFIRMED);
        }
        if (!account.status().acceptsCharges()) {
            throw new BillingException.AccountClosedForCharges(account.status().code());
        }
        if (activeLines().isEmpty()) {
            throw new BillingException.EmptySale();
        }
    }

    public void confirm(PricingTerms terms, Clock clock) {
        requireConfirmable();
        PricedSale priced = price(terms, clock.getZone());
        if (!priced.unauthorized().isEmpty()) {
            throw new BillingException.LinesWithoutAuthorization(priced.unauthorized().stream()
                    .map(line -> line.service().cupsCode()).distinct().toList());
        }
        if (!priced.pending().isEmpty()) {
            throw new BillingException.UnpricedLines(priced.pending().stream()
                    .map(line -> line.service().cupsCode()).distinct().toList());
        }
        activeLines().forEach(line -> line.settle(priced.lines().get(line.uuid())));
        terms.packages().forEach(charge -> packages.add(SalePackage.of(this, charge)));
        contractUuid = terms.contractUuid();
        contractNumber = terms.contractNumber();
        payerUuid = terms.payerUuid();
        linesTotal = priced.linesTotal();
        packagesTotal = priced.packagesTotal();
        total = priced.total();
        applyStatus(status().confirm(Instant.now(clock)));
    }

    public void cancel(String reason, Clock clock) {
        applyStatus(status().cancel(reason, Instant.now(clock)));
    }

    public SaleLine line(UUID lineUuid) {
        return lines.stream().filter(line -> line.uuid().equals(lineUuid)).findFirst()
                .orElseThrow(BillingException.LineNotFound::new);
    }

    public List<SaleLine> lines() {
        return List.copyOf(lines);
    }

    public List<SaleLine> activeLines() {
        return lines.stream().filter(line -> !line.removed()).toList();
    }

    public boolean charges(UUID portfolioItemUuid, UUID authorizationUuid) {
        return activeLines().stream().anyMatch(line -> line.service().portfolioItemUuid().equals(portfolioItemUuid)
                && line.origin() instanceof LineOrigin.Authorized authorized
                && authorized.authorizationUuid().equals(authorizationUuid));
    }

    private void requireEditable() {
        if (!status().editable()) {
            throw new BillingException.SaleNotEditable(statusCode);
        }
    }

    public SaleStatus status() {
        return switch (statusCode) {
            case DRAFT -> new SaleStatus.Draft();
            case CONFIRMED -> new SaleStatus.Confirmed(statusChangedAt);
            case CANCELLED -> new SaleStatus.Cancelled(statusReason, statusChangedAt);
        };
    }

    private void applyStatus(SaleStatus status) {
        statusCode = status.code();
        switch (status) {
            case SaleStatus.Draft ignored -> {
                statusReason = null;
                statusChangedAt = null;
            }
            case SaleStatus.Confirmed confirmed -> {
                statusReason = null;
                statusChangedAt = confirmed.since();
            }
            case SaleStatus.Cancelled cancelled -> {
                statusReason = cancelled.reason();
                statusChangedAt = cancelled.since();
            }
        }
    }

    public UUID uuid() {
        return uuid;
    }

    public long version() {
        return version;
    }

    public String number() {
        return number;
    }

    public SaleType type() {
        return SaleType.of(typeCode);
    }

    public EpisodeAccount account() {
        return account;
    }

    public List<SalePackage> packages() {
        return List.copyOf(packages);
    }

    public Optional<Settlement> settlement() {
        return total == null ? Optional.empty()
                : Optional.of(new Settlement(contractUuid, contractNumber, payerUuid, linesTotal, packagesTotal, total));
    }

    public record Settlement(UUID contractUuid, String contractNumber, UUID payerUuid, BigDecimal linesTotal,
                             BigDecimal packagesTotal, BigDecimal total) {
    }

    public Instant createdAt() {
        return createdAt;
    }
}

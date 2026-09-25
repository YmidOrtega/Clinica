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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL)
    @OrderBy("position")
    private List<SaleLine> lines = new ArrayList<>();

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
        return line;
    }

    public void removeLine(UUID lineUuid, String reason, Clock clock) {
        requireEditable();
        line(lineUuid).remove(reason, Instant.now(clock));
        activeLineCount--;
    }

    public void confirm(Clock clock) {
        if (!account.status().acceptsCharges()) {
            throw new BillingException.AccountClosedForCharges(account.status().code());
        }
        if (activeLines().isEmpty()) {
            throw new BillingException.EmptySale();
        }
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

    public Instant createdAt() {
        return createdAt;
    }
}

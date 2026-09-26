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
import org.hibernate.envers.NotAudited;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoices")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class Invoice {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_kind", nullable = false, updatable = false, length = 20)
    private AccountSummary.UnitKind unitKind;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sale_uuid", updatable = false, length = 36)
    private UUID saleUuid;

    @Column(name = "unit_key", nullable = false, updatable = false, length = 40)
    private String unitKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "buyer_kind", nullable = false, updatable = false, length = 20)
    private Buyer.Kind buyerKind;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "buyer_reference", nullable = false, updatable = false, length = 36)
    private UUID buyerReference;

    @Column(name = "buyer_document_type", nullable = false, updatable = false, length = 40)
    private String buyerDocumentType;

    @Column(name = "buyer_document_number", nullable = false, updatable = false, length = 20)
    private String buyerDocumentNumber;

    @Column(name = "buyer_name", nullable = false, updatable = false, length = 200)
    private String buyerName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "patient_uuid", nullable = false, updatable = false, length = 36)
    private UUID patientUuid;

    @Column(name = "patient_document_type", nullable = false, updatable = false, length = 40)
    private String patientDocumentType;

    @Column(name = "patient_document_number", nullable = false, updatable = false, length = 20)
    private String patientDocumentNumber;

    @Column(name = "patient_name", nullable = false, updatable = false, length = 200)
    private String patientName;

    @Column(name = "patient_health_regime", updatable = false, length = 40)
    private String patientHealthRegime;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "contract_uuid", updatable = false, length = 36)
    private UUID contractUuid;

    @Column(name = "contract_number", updatable = false, length = 40)
    private String contractNumber;

    @Column(name = "gross_total", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal grossTotal;

    @Column(name = "patient_share", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal patientShare;

    @Column(name = "payable_total", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal payableTotal;

    @Column(name = "patient_share_source", nullable = false, updatable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AccountSummary.ShareSource patientShareSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvoiceStatus.Code statusCode;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "prefix", length = 4)
    private String prefix;

    @Column(name = "consecutive")
    private Long consecutive;

    @Column(name = "number", length = 24)
    private String number;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "resolution_uuid", length = 36)
    private UUID resolutionUuid;

    @Column(name = "issued_on")
    private LocalDate issuedOn;

    @NotAudited
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.PERSIST)
    @OrderBy("position")
    private List<InvoiceLine> lines = new ArrayList<>();

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

    protected Invoice() {
    }

    public static Invoice draft(AccountSummary.Unit unit, EpisodeAccount account, Buyer buyer, HealthUser user) {
        DomainRules.required(unit, "unit");
        if (!unit.ready()) {
            throw new BillingException.NotABillableUnit(unit.notReadyReason());
        }
        if (unit.total().signum() <= 0) {
            throw new BillingException.NotABillableUnit("La unidad no tiene nada que cobrar");
        }
        Invoice invoice = new Invoice();
        invoice.uuid = UUID.randomUUID();
        invoice.account = DomainRules.required(account, "account");
        invoice.unitKind = unit.kind();
        invoice.saleUuid = unit.saleUuid();
        invoice.unitKey = keyOf(account, unit);
        DomainRules.required(buyer, "buyer");
        invoice.buyerKind = buyer.kind();
        invoice.buyerReference = buyer.reference();
        invoice.buyerDocumentType = buyer.documentType();
        invoice.buyerDocumentNumber = buyer.documentNumber();
        invoice.buyerName = buyer.name();
        DomainRules.required(user, "user");
        invoice.patientUuid = user.patientUuid();
        invoice.patientDocumentType = user.documentType();
        invoice.patientDocumentNumber = user.documentNumber();
        invoice.patientName = user.name();
        invoice.patientHealthRegime = user.healthRegime();
        unit.sales().stream().map(Sale::settlement).flatMap(java.util.Optional::stream)
                .filter(settled -> settled.contractUuid() != null).findFirst().ifPresent(settled -> {
                    invoice.contractUuid = settled.contractUuid();
                    invoice.contractNumber = settled.contractNumber();
                });
        invoice.grossTotal = unit.total();
        if (buyer.kind() == Buyer.Kind.PATIENT) {
            invoice.patientShare = Money.ZERO;
            invoice.payableTotal = unit.total();
        } else {
            invoice.patientShare = unit.patientShare();
            invoice.payableTotal = unit.payerShare();
        }
        invoice.patientShareSource = unit.shareSource();
        int position = 1;
        for (Sale sale : unit.sales()) {
            for (SaleLine line : sale.activeLines()) {
                invoice.lines.add(InvoiceLine.service(invoice, position++, sale, line));
            }
        }
        for (PackageCharge charge : unit.packages()) {
            invoice.lines.add(InvoiceLine.pack(invoice, position++, charge));
        }
        invoice.statusCode = InvoiceStatus.Code.DRAFT;
        return invoice;
    }

    public static String keyOf(EpisodeAccount account, AccountSummary.Unit unit) {
        return unit.kind() == AccountSummary.UnitKind.ACCOUNT ? "A:" + account.uuid() : "S:" + unit.saleUuid();
    }

    public boolean stillMatches(AccountSummary.Unit unit) {
        return keyOf(account, unit).equals(unitKey) && unit.total().compareTo(grossTotal) == 0
                && (buyerKind == Buyer.Kind.PATIENT || unit.patientShare().compareTo(patientShare) == 0);
    }

    public void issue(IssuedNumber issued, Clock clock) {
        applyStatus(status().issue(Instant.now(clock)));
        prefix = issued.prefix();
        consecutive = issued.consecutive();
        number = issued.formatted();
        resolutionUuid = issued.resolutionUuid();
        issuedOn = LocalDate.now(clock);
    }

    public void discard(String reason, Clock clock) {
        applyStatus(status().discard(reason, Instant.now(clock)));
    }

    public InvoiceStatus status() {
        return switch (statusCode) {
            case DRAFT -> new InvoiceStatus.Draft();
            case ISSUED -> new InvoiceStatus.Issued(statusChangedAt);
            case DISCARDED -> new InvoiceStatus.Discarded(statusReason, statusChangedAt);
        };
    }

    private void applyStatus(InvoiceStatus status) {
        statusCode = status.code();
        switch (status) {
            case InvoiceStatus.Draft ignored -> {
                statusReason = null;
                statusChangedAt = null;
            }
            case InvoiceStatus.Issued issued -> {
                statusReason = null;
                statusChangedAt = issued.at();
            }
            case InvoiceStatus.Discarded discarded -> {
                statusReason = discarded.reason();
                statusChangedAt = discarded.at();
            }
        }
    }

    public UUID uuid() {
        return uuid;
    }

    public long version() {
        return version;
    }

    public EpisodeAccount account() {
        return account;
    }

    public AccountSummary.UnitKind unitKind() {
        return unitKind;
    }

    public UUID saleUuid() {
        return saleUuid;
    }

    public Buyer buyer() {
        return new Buyer(buyerKind, buyerReference, buyerDocumentType, buyerDocumentNumber, buyerName);
    }

    public HealthUser user() {
        return new HealthUser(patientUuid, patientDocumentType, patientDocumentNumber, patientName, patientHealthRegime);
    }

    public UUID contractUuid() {
        return contractUuid;
    }

    public String contractNumber() {
        return contractNumber;
    }

    public BigDecimal grossTotal() {
        return grossTotal;
    }

    public BigDecimal patientShare() {
        return patientShare;
    }

    public BigDecimal payableTotal() {
        return payableTotal;
    }

    public AccountSummary.ShareSource patientShareSource() {
        return patientShareSource;
    }

    public String number() {
        return number;
    }

    public UUID resolutionUuid() {
        return resolutionUuid;
    }

    public LocalDate issuedOn() {
        return issuedOn;
    }

    public List<InvoiceLine> lines() {
        return List.copyOf(lines);
    }

    public Instant createdAt() {
        return createdAt;
    }
}

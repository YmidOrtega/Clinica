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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "invoices")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class Invoice {

    public enum Purpose {
        SERVICES,
        SHARED_PAYMENT
    }

    private static final Pattern COLLECTION_REFERENCE = Pattern.compile("^[A-Za-z0-9-]{1,38}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, updatable = false, unique = true, length = 36)
    private UUID uuid;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, updatable = false, length = 20)
    private Purpose purpose = Purpose.SERVICES;

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

    @Column(name = "patient_share", nullable = false, precision = 14, scale = 2)
    private BigDecimal patientShare;

    @Column(name = "payable_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal payableTotal;

    @Column(name = "expected_share", precision = 14, scale = 2)
    private BigDecimal expectedShare;

    @Enumerated(EnumType.STRING)
    @Column(name = "shared_payment_kind", updatable = false, length = 30)
    private SharedPaymentKind sharedPaymentKind;

    @Column(name = "authorization_number", updatable = false, length = 40)
    private String authorizationNumber;

    @Column(name = "collection_reference", updatable = false, length = 38)
    private String collectionReference;

    @NotAudited
    @ManyToMany
    @JoinTable(name = "invoice_shared_payments", joinColumns = @JoinColumn(name = "invoice_id"),
            inverseJoinColumns = @JoinColumn(name = "shared_invoice_id"))
    @OrderBy("id")
    private Set<Invoice> sharedPayments = new LinkedHashSet<>();

    @Column(name = "credited_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal creditedTotal = Money.ZERO;

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

    @Column(name = "issued_time")
    private java.time.LocalTime issuedTime;

    @Column(name = "cufe", length = 96)
    private String cufe;

    @Column(name = "qr_content", length = 1000)
    private String qrContent;

    @NotAudited
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.PERSIST)
    @OrderBy("position")
    private Set<InvoiceLine> lines = new LinkedHashSet<>();

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
        return draft(unit, account, buyer, user, List.of());
    }

    public static Invoice draft(AccountSummary.Unit unit, EpisodeAccount account, Buyer buyer, HealthUser user,
                                List<Invoice> shared) {
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
            invoice.expectedShare = unit.patientShare();
            invoice.deduct(shared);
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

    public static Invoice sharedPayment(EpisodeAccount account, Buyer patient, HealthUser user, SharedPaymentKind kind,
                                        BigDecimal amount, String authorizationNumber, String collectionReference,
                                        String contractNumber) {
        DomainRules.required(patient, "patient");
        if (patient.kind() != Buyer.Kind.PATIENT) {
            throw new IllegalArgumentException("A shared payment is invoiced to the patient");
        }
        Invoice invoice = new Invoice();
        invoice.uuid = UUID.randomUUID();
        invoice.purpose = Purpose.SHARED_PAYMENT;
        invoice.account = DomainRules.required(account, "account");
        invoice.unitKind = AccountSummary.UnitKind.ACCOUNT;
        invoice.collectionReference = DomainRules.requiredPattern(collectionReference, "collectionReference",
                COLLECTION_REFERENCE, "debe tener de 1 a 38 letras, dígitos o guiones");
        invoice.unitKey = "P:" + invoice.collectionReference;
        invoice.sharedPaymentKind = DomainRules.required(kind, "kind");
        invoice.authorizationNumber = authorizationNumber == null || authorizationNumber.isBlank() ? null
                : DomainRules.requiredText(authorizationNumber, "authorizationNumber", 40);
        invoice.buyerKind = patient.kind();
        invoice.buyerReference = patient.reference();
        invoice.buyerDocumentType = patient.documentType();
        invoice.buyerDocumentNumber = patient.documentNumber();
        invoice.buyerName = patient.name();
        DomainRules.required(user, "user");
        invoice.patientUuid = user.patientUuid();
        invoice.patientDocumentType = user.documentType();
        invoice.patientDocumentNumber = user.documentNumber();
        invoice.patientName = user.name();
        invoice.patientHealthRegime = user.healthRegime();
        invoice.contractNumber = contractNumber;
        invoice.grossTotal = Money.positive(amount, "amount");
        invoice.patientShare = Money.ZERO;
        invoice.payableTotal = invoice.grossTotal;
        invoice.patientShareSource = AccountSummary.ShareSource.PRIVATE;
        invoice.lines.add(InvoiceLine.sharedPayment(invoice, kind, invoice.authorizationNumber, invoice.grossTotal));
        invoice.statusCode = InvoiceStatus.Code.DRAFT;
        return invoice;
    }

    public void deduct(List<Invoice> shared) {
        if (purpose != Purpose.SERVICES || buyerKind != Buyer.Kind.PAYER) {
            throw new IllegalStateException("Only an invoice of services to a payer deducts shared payments");
        }
        if (statusCode != null && statusCode != InvoiceStatus.Code.DRAFT) {
            throw new IllegalStateException("The shared payments of an issued invoice never change");
        }
        BigDecimal invoiced = Money.ZERO;
        for (Invoice payment : shared) {
            if (payment.purpose != Purpose.SHARED_PAYMENT || !(payment.status() instanceof InvoiceStatus.Issued)
                    || !payment.account.uuid().equals(account.uuid())) {
                throw new IllegalArgumentException("Only issued shared payments of the same account are deducted");
            }
            invoiced = invoiced.add(payment.grossTotal);
        }
        invoiced = Money.of(invoiced);
        if (invoiced.compareTo(expectedShare) > 0) {
            throw new BillingException.SharedPaymentExceedsExpected(invoiced, expectedShare);
        }
        if (invoiced.compareTo(grossTotal) > 0) {
            throw new BillingException.SharedPaymentExceedsExpected(invoiced, grossTotal);
        }
        if (!new LinkedHashSet<>(shared).equals(sharedPayments)) {
            sharedPayments.clear();
            sharedPayments.addAll(shared);
        }
        patientShare = invoiced;
        payableTotal = Money.of(grossTotal.subtract(invoiced));
    }

    public BigDecimal shareShortfall() {
        return expectedShare == null ? Money.ZERO : Money.of(expectedShare.subtract(patientShare).max(Money.ZERO));
    }

    public boolean stillMatches(AccountSummary.Unit unit) {
        return keyOf(account, unit).equals(unitKey) && unit.total().compareTo(grossTotal) == 0
                && (buyerKind == Buyer.Kind.PATIENT || unit.patientShare().compareTo(expectedShare) == 0);
    }

    public void issue(IssuedNumber issued, Clock clock) {
        applyStatus(status().issue(Instant.now(clock)));
        prefix = issued.prefix();
        consecutive = issued.consecutive();
        number = issued.formatted();
        resolutionUuid = issued.resolutionUuid();
        issuedOn = LocalDate.now(clock);
        issuedTime = java.time.LocalTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    }

    public Cufe.Input cufeInput(Issuer issuer, NumberingResolution resolution) {
        if (!(status() instanceof InvoiceStatus.Issued)) {
            throw new IllegalStateException("Only an issued invoice has a CUFE");
        }
        return new Cufe.Input(number, issuedOn, issuedTime, grossTotal, Money.ZERO, Money.ZERO, Money.ZERO,
                payableTotal, issuer.nit().number(), Cufe.withoutVerificationDigit(buyerDocumentNumber),
                resolution.terms().technicalKey(), issuer.environment());
    }

    public void identify(String cufe, String qrContent) {
        if (this.cufe != null) {
            throw new IllegalStateException("The CUFE of an invoice never changes");
        }
        this.cufe = DomainRules.requiredText(cufe, "cufe", 96);
        this.qrContent = DomainRules.requiredText(qrContent, "qrContent", 1000);
    }

    public String cufe() {
        return cufe;
    }

    public String qrContent() {
        return qrContent;
    }

    public java.time.LocalTime issuedTime() {
        return issuedTime;
    }

    public void credit(CreditNote note) {
        DomainRules.required(note, "note");
        if (note.invoice() != this) {
            throw new IllegalArgumentException("The credit note belongs to another invoice");
        }
        BigDecimal credited = creditedTotal.add(note.creditedPayable());
        if (credited.compareTo(payableTotal) > 0) {
            throw new BillingException.CreditExceedsInvoice("Las notas crédito superarían el valor a pagar de la factura");
        }
        if (note.voids()) {
            applyStatus(status().voidBy("Anulada con la nota crédito " + note.number() + ": " + note.reason(),
                    note.issuedAt()));
        }
        creditedTotal = Money.of(credited);
    }

    public BigDecimal creditedTotal() {
        return creditedTotal;
    }

    public void discard(String reason, Clock clock) {
        applyStatus(status().discard(reason, Instant.now(clock)));
    }

    public InvoiceStatus status() {
        return switch (statusCode) {
            case DRAFT -> new InvoiceStatus.Draft();
            case ISSUED -> new InvoiceStatus.Issued(statusChangedAt);
            case DISCARDED -> new InvoiceStatus.Discarded(statusReason, statusChangedAt);
            case VOIDED -> new InvoiceStatus.Voided(statusReason, statusChangedAt);
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
            case InvoiceStatus.Voided voided -> {
                statusReason = voided.reason();
                statusChangedAt = voided.at();
            }
        }
    }

    public UUID uuid() {
        return uuid;
    }

    public Purpose purpose() {
        return purpose;
    }

    public SharedPaymentKind sharedPaymentKind() {
        return sharedPaymentKind;
    }

    public String authorizationNumber() {
        return authorizationNumber;
    }

    public String collectionReference() {
        return collectionReference;
    }

    public BigDecimal expectedShare() {
        return expectedShare;
    }

    public List<Invoice> sharedPayments() {
        return List.copyOf(sharedPayments);
    }

    public BigDecimal sharedPaymentOf(SharedPaymentKind kind) {
        return Money.of(sharedPayments.stream().filter(payment -> payment.sharedPaymentKind == kind)
                .map(Invoice::grossTotal).reduce(Money.ZERO, BigDecimal::add));
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

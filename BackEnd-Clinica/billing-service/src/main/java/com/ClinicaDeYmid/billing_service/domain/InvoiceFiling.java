package com.ClinicaDeYmid.billing_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.hibernate.envers.RelationTargetAuditMode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "invoice_filings")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class InvoiceFiling {

    public static final int BUSINESS_DAYS_TO_FILE = 22;

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
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false, unique = true)
    private Invoice invoice;

    @Column(name = "cuv", nullable = false, updatable = false, length = 96)
    private String cuv;

    @Column(name = "filing_number", nullable = false, length = 60)
    private String filingNumber;

    @Column(name = "filed_on", nullable = false)
    private LocalDate filedOn;

    @Column(name = "deadline", nullable = false, updatable = false)
    private LocalDate deadline;

    @Column(name = "correction_reason", length = 500)
    private String correctionReason;

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

    protected InvoiceFiling() {
    }

    public static InvoiceFiling register(RipsSubmission validated, String filingNumber, LocalDate filedOn,
                                         LocalDate validatedOn, LocalDate today) {
        DomainRules.required(validated, "validated");
        if (validated.status() != RipsSubmission.Status.VALIDATED) {
            throw new BillingException.FilingWithoutCuv();
        }
        Invoice invoice = validated.invoice();
        requireFileable(invoice);
        InvoiceFiling filing = new InvoiceFiling();
        filing.uuid = UUID.randomUUID();
        filing.invoice = invoice;
        filing.cuv = validated.cuv();
        filing.deadline = InvoiceFiling.deadlineOf(invoice.issuedOn());
        filing.apply(filingNumber, filedOn, validatedOn, today);
        return filing;
    }

    public static LocalDate deadlineOf(LocalDate issuedOn) {
        return BusinessCalendar.plusBusinessDays(DomainRules.required(issuedOn, "issuedOn"), BUSINESS_DAYS_TO_FILE);
    }

    public static void requireFileable(Invoice invoice) {
        DomainRules.required(invoice, "invoice");
        if (invoice.purpose() != Invoice.Purpose.SERVICES || invoice.buyer().kind() != Buyer.Kind.PAYER) {
            throw new BillingException.NotFileable("Solo se radica ante el pagador una factura de servicios");
        }
        if (!(invoice.status() instanceof InvoiceStatus.Issued)) {
            throw new BillingException.NotFileable("Solo se radica una factura emitida y vigente");
        }
    }

    public void correct(String filingNumber, LocalDate filedOn, String reason, LocalDate validatedOn, LocalDate today) {
        correctionReason = DomainRules.requiredText(reason, "reason", 500);
        apply(filingNumber, filedOn, validatedOn, today);
    }

    private void apply(String number, LocalDate on, LocalDate validatedOn, LocalDate today) {
        filingNumber = DomainRules.requiredText(number, "filingNumber", 60);
        DomainRules.required(on, "filedOn");
        if (on.isAfter(today)) {
            throw new BillingException.InvalidFilingDate("La fecha de radicación no puede ser futura");
        }
        if (on.isBefore(validatedOn)) {
            throw new BillingException.InvalidFilingDate(
                    "La fecha de radicación no puede ser anterior a la validación del RIPS (" + validatedOn + ")");
        }
        filedOn = on;
    }

    public boolean late() {
        return filedOn.isAfter(deadline);
    }

    public UUID uuid() {
        return uuid;
    }

    public long version() {
        return version;
    }

    public Invoice invoice() {
        return invoice;
    }

    public String cuv() {
        return cuv;
    }

    public String filingNumber() {
        return filingNumber;
    }

    public LocalDate filedOn() {
        return filedOn;
    }

    public LocalDate deadline() {
        return deadline;
    }

    public String correctionReason() {
        return correctionReason;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String createdBy() {
        return createdBy;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public String updatedBy() {
        return updatedBy;
    }
}

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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "credit_notes")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class CreditNote {

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
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "concept", nullable = false, updatable = false, length = 30)
    private CreditConcept concept;

    @Column(name = "reason", nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "prefix", nullable = false, updatable = false, length = 4)
    private String prefix;

    @Column(name = "consecutive", nullable = false, updatable = false)
    private long consecutive;

    @Column(name = "number", nullable = false, updatable = false, length = 24)
    private String number;

    @Column(name = "issued_on", nullable = false, updatable = false)
    private LocalDate issuedOn;

    @Column(name = "issued_time", nullable = false, updatable = false)
    private LocalTime issuedTime;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "credited_gross", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal creditedGross;

    @Column(name = "credited_share", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal creditedShare;

    @Column(name = "credited_payable", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal creditedPayable;

    @Column(name = "cude", nullable = false, length = 96)
    private String cude;

    @NotAudited
    @OneToMany(mappedBy = "note", cascade = CascadeType.PERSIST)
    @OrderBy("position")
    private List<CreditNoteLine> lines = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 36)
    private String createdBy;

    protected CreditNote() {
    }

    public static CreditNote issue(Invoice invoice, List<CreditNote> previous, CreditConcept concept, String reason,
                                   List<CreditRequest> requests, String prefix, long consecutive, Clock clock) {
        DomainRules.required(invoice, "invoice");
        DomainRules.required(concept, "concept");
        if (!(invoice.status() instanceof InvoiceStatus.Issued)) {
            throw new BillingException.InvoiceNotCreditable("Solo se acredita una factura emitida que no esté anulada");
        }
        CreditNote note = new CreditNote();
        note.uuid = UUID.randomUUID();
        note.invoice = invoice;
        note.concept = concept;
        note.reason = DomainRules.requiredText(reason, "reason", 500);
        note.prefix = DomainRules.requiredText(prefix, "prefix", 4);
        note.consecutive = consecutive;
        note.number = prefix + consecutive;
        note.issuedAt = Instant.now(clock);
        note.issuedOn = LocalDate.now(clock);
        note.issuedTime = LocalTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        List<InvoiceLine> creditable = invoice.lines().stream().filter(line -> line.lineTotal().signum() > 0).toList();
        if (concept == CreditConcept.VOID) {
            note.voidAll(invoice, previous, requests, creditable);
        } else {
            note.creditSome(invoice, previous, requests, creditable);
        }
        return note;
    }

    private void voidAll(Invoice invoice, List<CreditNote> previous, List<CreditRequest> requests,
                         List<InvoiceLine> creditable) {
        if (requests != null && !requests.isEmpty()) {
            throw new BillingException.InvalidData("lines", "no aplica: la anulación acredita la factura completa");
        }
        if (!previous.isEmpty()) {
            throw new BillingException.InvoiceNotCreditable(
                    "La factura ya tiene notas crédito; acredite el saldo con notas parciales");
        }
        int position = 1;
        for (InvoiceLine line : creditable) {
            lines.add(CreditNoteLine.of(this, position++, line, line.quantity(), line.unitPrice()));
        }
        creditedGross = invoice.grossTotal();
        creditedShare = invoice.patientShare();
        creditedPayable = invoice.payableTotal();
    }

    private void creditSome(Invoice invoice, List<CreditNote> previous, List<CreditRequest> requests,
                            List<InvoiceLine> creditable) {
        if (requests == null || requests.isEmpty()) {
            throw new BillingException.InvalidData("lines", "debe indicar al menos una línea a acreditar");
        }
        Map<Integer, InvoiceLine> byPosition = new HashMap<>();
        creditable.forEach(line -> byPosition.put(line.position(), line));
        Map<Integer, BigDecimal> alreadyCredited = new HashMap<>();
        previous.forEach(note -> note.lines().forEach(line ->
                alreadyCredited.merge(line.invoiceLinePosition(), line.lineTotal(), BigDecimal::add)));
        Set<Integer> seen = new HashSet<>();
        BigDecimal total = Money.ZERO;
        int position = 1;
        for (CreditRequest request : requests) {
            InvoiceLine credited = byPosition.get(request.invoiceLinePosition());
            if (credited == null) {
                throw new BillingException.InvalidData("lines.invoiceLinePosition",
                        "no corresponde a una línea con valor de la factura: " + request.invoiceLinePosition());
            }
            if (!seen.add(credited.position())) {
                throw new BillingException.InvalidData("lines.invoiceLinePosition",
                        "está repetida: " + credited.position());
            }
            CreditNoteLine line;
            if (request.quantity() != null) {
                if (request.quantity() > credited.quantity()) {
                    throw new BillingException.CreditExceedsInvoice("La línea " + credited.position()
                            + " solo tiene " + credited.quantity() + " unidades facturadas");
                }
                line = CreditNoteLine.of(this, position++, credited, request.quantity(), credited.unitPrice());
            } else {
                line = CreditNoteLine.of(this, position++, credited, 1,
                        Money.positive(request.amount(), "lines.amount"));
            }
            BigDecimal lineCredited = alreadyCredited.getOrDefault(credited.position(), Money.ZERO)
                    .add(line.lineTotal());
            if (lineCredited.compareTo(credited.lineTotal()) > 0) {
                throw new BillingException.CreditExceedsInvoice("La línea " + credited.position()
                        + " quedaría acreditada por más de lo facturado (" + Money.of(credited.lineTotal()) + ")");
            }
            lines.add(line);
            total = total.add(line.lineTotal());
        }
        creditedGross = Money.of(total);
        creditedShare = Money.ZERO;
        creditedPayable = creditedGross;
        if (invoice.creditedTotal().add(creditedPayable).compareTo(invoice.payableTotal()) > 0) {
            throw new BillingException.CreditExceedsInvoice("Las notas crédito superarían el valor a pagar de la factura ("
                    + Money.of(invoice.payableTotal()) + ")");
        }
    }

    public Cufe.Input cudeInput(Issuer issuer, String softwarePin) {
        return new Cufe.Input(number, issuedOn, issuedTime, creditedGross, Money.ZERO, Money.ZERO, Money.ZERO,
                creditedPayable, issuer.nit().number(), Cufe.withoutVerificationDigit(invoice.buyer().documentNumber()),
                softwarePin, issuer.environment());
    }

    public void identify(String cude) {
        if (this.cude != null) {
            throw new IllegalStateException("The CUDE of a credit note never changes");
        }
        this.cude = DomainRules.requiredText(cude, "cude", 96);
    }

    public boolean voids() {
        return concept == CreditConcept.VOID;
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

    public CreditConcept concept() {
        return concept;
    }

    public String reason() {
        return reason;
    }

    public String number() {
        return number;
    }

    public LocalDate issuedOn() {
        return issuedOn;
    }

    public LocalTime issuedTime() {
        return issuedTime;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public BigDecimal creditedGross() {
        return creditedGross;
    }

    public BigDecimal creditedShare() {
        return creditedShare;
    }

    public BigDecimal creditedPayable() {
        return creditedPayable;
    }

    public String cude() {
        return cude;
    }

    public List<CreditNoteLine> lines() {
        return lines;
    }

    public Instant createdAt() {
        return createdAt;
    }
}

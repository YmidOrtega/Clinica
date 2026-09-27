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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Entity
@Table(name = "payer_objections")
@Audited
@EntityListeners(AuditingEntityListener.class)
public class PayerObjection {

    public enum Kind {
        DEVOLUTION(5, 5, 5),
        GLOSS(20, 15, 10);

        private final int daysToFormulate;
        private final int daysToRespond;
        private final int daysToDecide;

        Kind(int daysToFormulate, int daysToRespond, int daysToDecide) {
            this.daysToFormulate = daysToFormulate;
            this.daysToRespond = daysToRespond;
            this.daysToDecide = daysToDecide;
        }

        public int daysToFormulate() {
            return daysToFormulate;
        }

        public int daysToRespond() {
            return daysToRespond;
        }

        public int daysToDecide() {
            return daysToDecide;
        }

        ObjectionCode.Kind codeKind() {
            return this == DEVOLUTION ? ObjectionCode.Kind.DEVOLUTION : ObjectionCode.Kind.GLOSS;
        }
    }

    public enum Status {
        AWAITING_RESPONSE,
        RESPONDED,
        DECIDED
    }

    public enum Outcome {
        LIFTED,
        PARTIALLY_LIFTED,
        UPHELD
    }

    public record Item(Integer invoiceLinePosition, String code, BigDecimal amount, String detail) {
    }

    public record Answer(int position, String code, BigDecimal acceptedAmount, String detail) {
    }

    public record Ruling(int position, BigDecimal upheldAmount) {
    }

    private static final Map<Kind, Set<String>> RESPONSES = Map.of(
            Kind.DEVOLUTION, Set.of("RE9501", "RE9601", "RE9701"),
            Kind.GLOSS, Set.of("RE9502", "RE9602", "RE9702", "RE9801", "RE9901"));
    private static final Set<String> FULL_ACCEPTANCE = Set.of("RE9701", "RE9702");
    private static final String PARTIAL_ACCEPTANCE = "RE9801";
    private static final int MAX_ITEMS = 500;

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
    @Column(name = "kind", nullable = false, updatable = false, length = 20)
    private Kind kind;

    @Column(name = "payer_record", nullable = false, updatable = false, length = 60)
    private String payerRecord;

    @Column(name = "notified_on", nullable = false, updatable = false)
    private LocalDate notifiedOn;

    @Column(name = "formulation_deadline", nullable = false, updatable = false)
    private LocalDate formulationDeadline;

    @Column(name = "response_deadline", nullable = false, updatable = false)
    private LocalDate responseDeadline;

    @Column(name = "claimed_amount", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal claimedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "response_record", length = 60)
    private String responseRecord;

    @Column(name = "responded_on")
    private LocalDate respondedOn;

    @Column(name = "accepted_amount", precision = 14, scale = 2)
    private BigDecimal acceptedAmount;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_note_id")
    private CreditNote creditNote;

    @Column(name = "decided_on")
    private LocalDate decidedOn;

    @Column(name = "upheld_amount", precision = 14, scale = 2)
    private BigDecimal upheldAmount;

    @NotAudited
    @OneToMany(mappedBy = "objection", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @OrderBy("position")
    private List<ObjectionItem> items = new ArrayList<>();

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

    protected PayerObjection() {
    }

    public static PayerObjection register(InvoiceFiling filing, Kind kind, String payerRecord, LocalDate notifiedOn,
                                          List<Item> items, List<PayerObjection> earlier,
                                          Function<String, Optional<ObjectionCode>> catalog, LocalDate today) {
        if (filing == null) {
            throw new BillingException.InvoiceNotFiled();
        }
        Invoice invoice = filing.invoice();
        if (!(invoice.status() instanceof InvoiceStatus.Issued)) {
            throw new BillingException.InvalidObjection("La factura ya no está vigente");
        }
        DomainRules.required(kind, "kind");
        DomainRules.required(notifiedOn, "notifiedOn");
        if (notifiedOn.isAfter(today) || notifiedOn.isBefore(filing.filedOn())) {
            throw new BillingException.InvalidObjection(
                    "La fecha de notificación va desde la radicación (" + filing.filedOn() + ") hasta hoy");
        }
        PayerObjection objection = new PayerObjection();
        objection.uuid = UUID.randomUUID();
        objection.invoice = invoice;
        objection.kind = kind;
        objection.payerRecord = DomainRules.requiredText(payerRecord, "payerRecord", 60);
        objection.notifiedOn = notifiedOn;
        objection.formulationDeadline = BusinessCalendar.plusBusinessDays(filing.filedOn(), kind.daysToFormulate());
        objection.responseDeadline = BusinessCalendar.plusBusinessDays(notifiedOn, kind.daysToRespond());
        objection.status = Status.AWAITING_RESPONSE;
        BigDecimal outstanding = Money.of(invoice.payableTotal().subtract(invoice.creditedTotal()));
        if (kind == Kind.DEVOLUTION) {
            objection.addDevolution(items, earlier, catalog, outstanding);
        } else {
            objection.addGlosses(invoice, items, catalog, outstanding);
        }
        objection.claimedAmount = Money.of(objection.items.stream().map(ObjectionItem::amount)
                .reduce(Money.ZERO, BigDecimal::add));
        return objection;
    }

    private void addDevolution(List<Item> requested, List<PayerObjection> earlier,
                               Function<String, Optional<ObjectionCode>> catalog, BigDecimal outstanding) {
        if (earlier.stream().anyMatch(objection -> objection.kind == Kind.DEVOLUTION)) {
            throw new BillingException.InvalidObjection("El pagador solo puede devolver una factura una vez");
        }
        if (requested == null || requested.size() != 1) {
            throw new BillingException.InvalidData("items", "una devolución tiene una sola causal");
        }
        Item item = requested.getFirst();
        if (item.invoiceLinePosition() != null) {
            throw new BillingException.InvalidData("items.invoiceLinePosition", "una devolución afecta la factura completa");
        }
        items.add(ObjectionItem.of(this, 1, null, code(item.code(), catalog), outstanding, item.detail()));
    }

    private void addGlosses(Invoice invoice, List<Item> requested, Function<String, Optional<ObjectionCode>> catalog,
                            BigDecimal outstanding) {
        if (requested == null || requested.isEmpty() || requested.size() > MAX_ITEMS) {
            throw new BillingException.InvalidData("items", "una glosa tiene de 1 a " + MAX_ITEMS + " causales");
        }
        Map<Integer, InvoiceLine> lines = new HashMap<>();
        invoice.lines().stream().filter(line -> line.lineTotal().signum() > 0)
                .forEach(line -> lines.put(line.position(), line));
        Map<Integer, BigDecimal> perLine = new HashMap<>();
        BigDecimal total = Money.ZERO;
        int position = 1;
        for (Item item : requested) {
            InvoiceLine line = item.invoiceLinePosition() == null ? null : lines.get(item.invoiceLinePosition());
            if (line == null) {
                throw new BillingException.InvalidData("items.invoiceLinePosition",
                        "cada glosa señala una línea con valor de la factura: " + item.invoiceLinePosition());
            }
            BigDecimal amount = Money.positive(item.amount(), "items.amount");
            BigDecimal onLine = perLine.merge(line.position(), amount, BigDecimal::add);
            if (onLine.compareTo(line.lineTotal()) > 0) {
                throw new BillingException.InvalidData("items.amount", "la línea " + line.position()
                        + " quedaría glosada por más de lo facturado (" + Money.of(line.lineTotal()) + ")");
            }
            total = total.add(amount);
            items.add(ObjectionItem.of(this, position++, line.position(), code(item.code(), catalog), amount,
                    item.detail()));
        }
        if (total.compareTo(outstanding) > 0) {
            throw new BillingException.InvalidData("items.amount",
                    "la glosa supera el saldo de la factura (" + outstanding + ")");
        }
    }

    private String code(String code, Function<String, Optional<ObjectionCode>> catalog) {
        ObjectionCode found = catalog.apply(DomainRules.requiredText(code, "code", 6)).orElseThrow(() ->
                new BillingException.InvalidData("items.code", "no existe en el manual único: " + code));
        if (found.kind() != kind.codeKind() || !found.applicable()) {
            throw new BillingException.InvalidData("items.code", code + " no es una causal de "
                    + (kind == Kind.DEVOLUTION ? "devolución" : "glosa") + " aplicable");
        }
        return found.code();
    }

    public void respond(String record, LocalDate on, List<Answer> answers, LocalDate today) {
        if (status != Status.AWAITING_RESPONSE) {
            throw new BillingException.InvalidObjection("La " + label() + " ya tiene respuesta");
        }
        DomainRules.required(on, "respondedOn");
        if (on.isBefore(notifiedOn) || on.isAfter(today)) {
            throw new BillingException.InvalidObjection(
                    "La fecha de respuesta va desde la notificación (" + notifiedOn + ") hasta hoy");
        }
        Map<Integer, Answer> byPosition = byPosition(answers, Answer::position, "answers");
        BigDecimal accepted = Money.ZERO;
        for (ObjectionItem item : items) {
            Answer answer = byPosition.get(item.position());
            String code = DomainRules.requiredText(answer.code(), "answers.code", 6);
            if (!RESPONSES.get(kind).contains(code)) {
                throw new BillingException.InvalidData("answers.code",
                        code + " no es una respuesta del prestador a una " + label());
            }
            BigDecimal amount = answer.acceptedAmount() == null ? Money.ZERO : Money.of(answer.acceptedAmount());
            boolean consistent = FULL_ACCEPTANCE.contains(code) ? amount.compareTo(item.amount()) == 0
                    : PARTIAL_ACCEPTANCE.equals(code)
                    ? amount.signum() > 0 && amount.compareTo(item.amount()) < 0
                    : amount.signum() == 0;
            if (!consistent) {
                throw new BillingException.InvalidData("answers.acceptedAmount", "el valor aceptado no corresponde a "
                        + code + " en la causal " + item.position());
            }
            item.answer(code, amount, answer.detail());
            accepted = accepted.add(amount);
        }
        responseRecord = DomainRules.requiredText(record, "responseRecord", 60);
        respondedOn = on;
        acceptedAmount = Money.of(accepted);
        status = Status.RESPONDED;
    }

    public void settledBy(CreditNote note) {
        if (creditNote != null) {
            throw new IllegalStateException("The objection already has its credit note");
        }
        creditNote = DomainRules.required(note, "note");
    }

    public void decide(LocalDate on, List<Ruling> rulings, LocalDate today) {
        if (status != Status.RESPONDED) {
            throw new BillingException.InvalidObjection("Solo se registra la decisión del pagador sobre una respuesta");
        }
        DomainRules.required(on, "decidedOn");
        if (on.isBefore(respondedOn) || on.isAfter(today)) {
            throw new BillingException.InvalidObjection(
                    "La fecha de la decisión va desde la respuesta (" + respondedOn + ") hasta hoy");
        }
        Map<Integer, Ruling> byPosition = byPosition(rulings, Ruling::position, "rulings");
        BigDecimal upheld = Money.ZERO;
        for (ObjectionItem item : items) {
            BigDecimal amount = byPosition.get(item.position()).upheldAmount() == null ? Money.ZERO
                    : Money.of(byPosition.get(item.position()).upheldAmount());
            if (amount.signum() < 0 || amount.compareTo(item.disputed()) > 0) {
                throw new BillingException.InvalidData("rulings.upheldAmount", "la causal " + item.position()
                        + " solo puede quedar en firme hasta por " + item.disputed());
            }
            item.decide(amount);
            upheld = upheld.add(amount);
        }
        decidedOn = on;
        upheldAmount = Money.of(upheld);
        status = Status.DECIDED;
    }

    private <T> Map<Integer, T> byPosition(List<T> entries, Function<T, Integer> position, String field) {
        if (entries == null || entries.size() != items.size()) {
            throw new BillingException.InvalidData(field, "debe tener una entrada por cada causal (" + items.size() + ")");
        }
        Map<Integer, T> found = new LinkedHashMap<>();
        Set<Integer> expected = new HashSet<>();
        items.forEach(item -> expected.add(item.position()));
        for (T entry : entries) {
            Integer at = position.apply(entry);
            if (!expected.contains(at) || found.put(at, entry) != null) {
                throw new BillingException.InvalidData(field, "la causal " + at + " no existe o está repetida");
            }
        }
        return found;
    }

    public List<CreditRequest> acceptedByLine() {
        Map<Integer, BigDecimal> perLine = new LinkedHashMap<>();
        items.stream().filter(item -> item.acceptedAmount() != null && item.acceptedAmount().signum() > 0)
                .forEach(item -> perLine.merge(item.invoiceLinePosition(), item.acceptedAmount(), BigDecimal::add));
        return perLine.entrySet().stream()
                .map(entry -> new CreditRequest(entry.getKey(), null, Money.of(entry.getValue()))).toList();
    }

    public boolean extemporaneous() {
        return notifiedOn.isAfter(formulationDeadline);
    }

    public boolean respondedLate() {
        return respondedOn != null && respondedOn.isAfter(responseDeadline);
    }

    public LocalDate decisionDeadline() {
        return respondedOn == null ? null : BusinessCalendar.plusBusinessDays(respondedOn, kind.daysToDecide());
    }

    public Outcome outcome() {
        if (status != Status.DECIDED) {
            return null;
        }
        BigDecimal disputed = Money.of(claimedAmount.subtract(acceptedAmount));
        if (upheldAmount.signum() == 0) {
            return Outcome.LIFTED;
        }
        return upheldAmount.compareTo(disputed) == 0 ? Outcome.UPHELD : Outcome.PARTIALLY_LIFTED;
    }

    private String label() {
        return kind == Kind.DEVOLUTION ? "devolución" : "glosa";
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

    public Kind kind() {
        return kind;
    }

    public String payerRecord() {
        return payerRecord;
    }

    public LocalDate notifiedOn() {
        return notifiedOn;
    }

    public LocalDate formulationDeadline() {
        return formulationDeadline;
    }

    public LocalDate responseDeadline() {
        return responseDeadline;
    }

    public BigDecimal claimedAmount() {
        return claimedAmount;
    }

    public Status status() {
        return status;
    }

    public String responseRecord() {
        return responseRecord;
    }

    public LocalDate respondedOn() {
        return respondedOn;
    }

    public BigDecimal acceptedAmount() {
        return acceptedAmount;
    }

    public CreditNote creditNote() {
        return creditNote;
    }

    public LocalDate decidedOn() {
        return decidedOn;
    }

    public BigDecimal upheldAmount() {
        return upheldAmount;
    }

    public List<ObjectionItem> items() {
        return List.copyOf(items);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String createdBy() {
        return createdBy;
    }
}

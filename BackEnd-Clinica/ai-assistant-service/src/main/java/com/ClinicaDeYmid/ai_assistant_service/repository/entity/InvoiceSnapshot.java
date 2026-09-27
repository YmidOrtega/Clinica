package com.ClinicaDeYmid.ai_assistant_service.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "invoices")
public class InvoiceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_uuid", nullable = false, updatable = false, unique = true)
    private UUID invoiceUuid;

    @Column(name = "number", nullable = false, length = 24)
    private String number;

    @Column(name = "purpose", nullable = false, length = 20)
    private String purpose;

    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "issued_on", nullable = false)
    private LocalDate issuedOn;

    @Column(name = "admission_number", nullable = false, length = 40)
    private String admissionNumber;

    @Column(name = "buyer_kind", nullable = false, length = 10)
    private String buyerKind;

    @Column(name = "payer_nit", length = 20)
    private String payerNit;

    @Column(name = "contract_number", length = 40)
    private String contractNumber;

    @Column(name = "uncontracted_care", length = 40)
    private String uncontractedCare;

    @Column(name = "payable_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal payableTotal;

    @Column(name = "credited_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal creditedTotal;

    @Column(name = "balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal balance;

    @Column(name = "share_shortfall", precision = 14, scale = 2)
    private BigDecimal shareShortfall;

    @Column(name = "dian_status", length = 20)
    private String dianStatus;

    @Column(name = "dian_status_at")
    private Instant dianStatusAt;

    @Column(name = "cuv", length = 96)
    private String cuv;

    @Column(name = "filing_number", length = 60)
    private String filingNumber;

    @Column(name = "filed_on")
    private LocalDate filedOn;

    @Column(name = "last_event_type", nullable = false, length = 40)
    private String lastEventType;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "state", nullable = false, columnDefinition = "jsonb")
    private String state;

    protected InvoiceSnapshot() {
    }

    public static InvoiceSnapshot of(UUID invoiceUuid) {
        InvoiceSnapshot snapshot = new InvoiceSnapshot();
        snapshot.invoiceUuid = invoiceUuid;
        return snapshot;
    }

    public boolean isNewerThanWhatIHave(Instant occurredAt) {
        return lastEventAt == null || occurredAt.isAfter(lastEventAt);
    }

    public void follow(String eventType, Instant occurredAt, String number, String purpose, String status,
                       LocalDate issuedOn, String admissionNumber, String buyerKind, String payerNit,
                       String contractNumber, String uncontractedCare, BigDecimal payableTotal,
                       BigDecimal creditedTotal, BigDecimal balance, BigDecimal shareShortfall, String dianStatus,
                       Instant dianStatusAt, String cuv, String filingNumber, LocalDate filedOn, String state) {
        this.lastEventType = eventType;
        this.lastEventAt = occurredAt;
        this.number = number;
        this.purpose = purpose;
        this.status = status;
        this.issuedOn = issuedOn;
        this.admissionNumber = admissionNumber;
        this.buyerKind = buyerKind;
        this.payerNit = payerNit;
        this.contractNumber = contractNumber;
        this.uncontractedCare = uncontractedCare;
        this.payableTotal = payableTotal;
        this.creditedTotal = creditedTotal;
        this.balance = balance;
        this.shareShortfall = shareShortfall;
        this.dianStatus = dianStatus;
        this.dianStatusAt = dianStatusAt;
        this.cuv = cuv;
        this.filingNumber = filingNumber;
        this.filedOn = filedOn;
        this.state = state;
    }

    public UUID invoiceUuid() {
        return invoiceUuid;
    }

    public String number() {
        return number;
    }

    public String purpose() {
        return purpose;
    }

    public String status() {
        return status;
    }

    public LocalDate issuedOn() {
        return issuedOn;
    }

    public String admissionNumber() {
        return admissionNumber;
    }

    public String buyerKind() {
        return buyerKind;
    }

    public String payerNit() {
        return payerNit;
    }

    public String contractNumber() {
        return contractNumber;
    }

    public String uncontractedCare() {
        return uncontractedCare;
    }

    public BigDecimal payableTotal() {
        return payableTotal;
    }

    public BigDecimal creditedTotal() {
        return creditedTotal;
    }

    public BigDecimal balance() {
        return balance;
    }

    public BigDecimal shareShortfall() {
        return shareShortfall;
    }

    public String dianStatus() {
        return dianStatus;
    }

    public Instant dianStatusAt() {
        return dianStatusAt;
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

    public String lastEventType() {
        return lastEventType;
    }

    public Instant lastEventAt() {
        return lastEventAt;
    }

    public String state() {
        return state;
    }
}

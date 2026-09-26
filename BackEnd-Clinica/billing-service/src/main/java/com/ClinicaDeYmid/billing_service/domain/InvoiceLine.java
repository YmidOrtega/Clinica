package com.ClinicaDeYmid.billing_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "invoice_lines")
public class InvoiceLine {

    public enum Kind {
        SERVICE,
        PACKAGE,
        SHARED_PAYMENT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Column(name = "position", nullable = false, updatable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, length = 20)
    private Kind kind;

    @Column(name = "sale_number", updatable = false, length = 20)
    private String saleNumber;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sale_line_uuid", updatable = false, length = 36)
    private UUID saleLineUuid;

    @Column(name = "code", nullable = false, updatable = false, length = 40)
    private String code;

    @Column(name = "description", nullable = false, updatable = false, length = 300)
    private String description;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "price_origin", updatable = false, length = 30)
    private String priceOrigin;

    @Column(name = "service_date", updatable = false)
    private LocalDate serviceDate;

    @Column(name = "authorization_number", updatable = false, length = 40)
    private String authorizationNumber;

    protected InvoiceLine() {
    }

    static InvoiceLine service(Invoice invoice, int position, Sale sale, SaleLine line) {
        LinePrice price = line.price().orElseThrow(() -> new IllegalStateException("Only settled lines are invoiced"));
        InvoiceLine invoiced = new InvoiceLine();
        invoiced.invoice = invoice;
        invoiced.position = position;
        invoiced.kind = Kind.SERVICE;
        invoiced.saleNumber = sale.number();
        invoiced.saleLineUuid = line.uuid();
        invoiced.code = line.service().cupsCode();
        invoiced.description = line.service().description();
        invoiced.quantity = line.quantity();
        invoiced.unitPrice = price.unitPrice();
        invoiced.lineTotal = price.lineTotal();
        invoiced.priceOrigin = price.origin().name();
        invoiced.serviceDate = line.serviceDate();
        invoiced.authorizationNumber = line.origin() instanceof LineOrigin.Authorized authorized
                ? authorized.authorizationNumber() : null;
        return invoiced;
    }

    static InvoiceLine sharedPayment(Invoice invoice, SharedPaymentKind kind, String authorizationNumber,
                                     BigDecimal amount) {
        InvoiceLine invoiced = new InvoiceLine();
        invoiced.invoice = invoice;
        invoiced.position = 1;
        invoiced.kind = Kind.SHARED_PAYMENT;
        invoiced.code = kind.healthField();
        invoiced.description = kind.label() + (authorizationNumber == null ? " de servicios de salud"
                : " · autorización " + authorizationNumber);
        invoiced.quantity = 1;
        invoiced.unitPrice = amount;
        invoiced.lineTotal = amount;
        invoiced.authorizationNumber = authorizationNumber;
        return invoiced;
    }

    static InvoiceLine pack(Invoice invoice, int position, PackageCharge charge) {
        InvoiceLine invoiced = new InvoiceLine();
        invoiced.invoice = invoice;
        invoiced.position = position;
        invoiced.kind = Kind.PACKAGE;
        invoiced.code = charge.code();
        invoiced.description = charge.name();
        invoiced.quantity = 1;
        invoiced.unitPrice = charge.price();
        invoiced.lineTotal = charge.price();
        invoiced.priceOrigin = PriceOrigin.PACKAGE.name();
        return invoiced;
    }

    public int position() {
        return position;
    }

    public Kind kind() {
        return kind;
    }

    public String saleNumber() {
        return saleNumber;
    }

    public String code() {
        return code;
    }

    public String description() {
        return description;
    }

    public int quantity() {
        return quantity;
    }

    public BigDecimal unitPrice() {
        return unitPrice;
    }

    public BigDecimal lineTotal() {
        return lineTotal;
    }

    public String priceOrigin() {
        return priceOrigin;
    }

    public LocalDate serviceDate() {
        return serviceDate;
    }

    public String authorizationNumber() {
        return authorizationNumber;
    }
}

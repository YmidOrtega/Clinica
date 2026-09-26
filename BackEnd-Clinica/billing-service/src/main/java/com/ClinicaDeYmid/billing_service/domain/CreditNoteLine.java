package com.ClinicaDeYmid.billing_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "credit_note_lines")
public class CreditNoteLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_note_id", nullable = false, updatable = false)
    private CreditNote note;

    @Column(name = "position", nullable = false, updatable = false)
    private int position;

    @Column(name = "invoice_line_position", nullable = false, updatable = false)
    private int invoiceLinePosition;

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

    protected CreditNoteLine() {
    }

    static CreditNoteLine of(CreditNote note, int position, InvoiceLine credited, int quantity, BigDecimal unitPrice) {
        CreditNoteLine line = new CreditNoteLine();
        line.note = note;
        line.position = position;
        line.invoiceLinePosition = credited.position();
        line.code = credited.code();
        line.description = credited.description();
        line.quantity = quantity;
        line.unitPrice = Money.of(unitPrice);
        line.lineTotal = Money.of(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        return line;
    }

    public int position() {
        return position;
    }

    public int invoiceLinePosition() {
        return invoiceLinePosition;
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
}

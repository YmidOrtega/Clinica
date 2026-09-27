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
@Table(name = "payer_objection_items")
public class ObjectionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "objection_id", nullable = false, updatable = false)
    private PayerObjection objection;

    @Column(name = "position", nullable = false, updatable = false)
    private int position;

    @Column(name = "invoice_line_position", updatable = false)
    private Integer invoiceLinePosition;

    @Column(name = "code", nullable = false, updatable = false, length = 6)
    private String code;

    @Column(name = "amount", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "detail", updatable = false, length = 500)
    private String detail;

    @Column(name = "response_code", length = 6)
    private String responseCode;

    @Column(name = "accepted_amount", precision = 14, scale = 2)
    private BigDecimal acceptedAmount;

    @Column(name = "response_detail", length = 1000)
    private String responseDetail;

    @Column(name = "upheld_amount", precision = 14, scale = 2)
    private BigDecimal upheldAmount;

    protected ObjectionItem() {
    }

    static ObjectionItem of(PayerObjection objection, int position, Integer invoiceLinePosition, String code,
                            BigDecimal amount, String detail) {
        ObjectionItem item = new ObjectionItem();
        item.objection = objection;
        item.position = position;
        item.invoiceLinePosition = invoiceLinePosition;
        item.code = code;
        item.amount = amount;
        item.detail = detail == null || detail.isBlank() ? null : DomainRules.requiredText(detail, "detail", 500);
        return item;
    }

    void answer(String code, BigDecimal accepted, String detail) {
        responseCode = code;
        acceptedAmount = accepted;
        responseDetail = detail == null || detail.isBlank() ? null
                : DomainRules.requiredText(detail, "responseDetail", 1000);
    }

    void decide(BigDecimal upheld) {
        upheldAmount = upheld;
    }

    public BigDecimal disputed() {
        return Money.of(amount.subtract(acceptedAmount == null ? Money.ZERO : acceptedAmount));
    }

    public int position() {
        return position;
    }

    public Integer invoiceLinePosition() {
        return invoiceLinePosition;
    }

    public String code() {
        return code;
    }

    public BigDecimal amount() {
        return amount;
    }

    public String detail() {
        return detail;
    }

    public String responseCode() {
        return responseCode;
    }

    public BigDecimal acceptedAmount() {
        return acceptedAmount;
    }

    public String responseDetail() {
        return responseDetail;
    }

    public BigDecimal upheldAmount() {
        return upheldAmount;
    }
}

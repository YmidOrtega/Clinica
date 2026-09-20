package com.ClinicaDeYmid.practitioners_service.repository.entity;

import com.ClinicaDeYmid.practitioners_service.shared.Rules;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.util.regex.Pattern;

@Entity
@Table(name = "fee_agreement_lines")
@Audited
public class FeeAgreementLine {

    private static final Pattern SERVICE_CODE = Pattern.compile("^[0-9]{6,8}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agreement_id", nullable = false, updatable = false)
    private FeeAgreement agreement;

    @Column(name = "service_code", nullable = false, updatable = false, length = 8)
    private String serviceCode;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    protected FeeAgreementLine() {
    }

    static FeeAgreementLine of(FeeAgreement agreement, String serviceCode, BigDecimal amount) {
        FeeAgreementLine line = new FeeAgreementLine();
        line.agreement = agreement;
        line.serviceCode = Rules.matching(Rules.requiredText(serviceCode, "lines.serviceCode", 8), SERVICE_CODE,
                "lines.serviceCode");
        line.amount = amount;
        return line;
    }

    public String serviceCode() {
        return serviceCode;
    }

    public BigDecimal amount() {
        return amount;
    }
}

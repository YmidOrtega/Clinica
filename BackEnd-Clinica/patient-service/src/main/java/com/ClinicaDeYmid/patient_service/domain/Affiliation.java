package com.ClinicaDeYmid.patient_service.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Embeddable
public class Affiliation {

    private static final Pattern POLICY = Pattern.compile("^[A-Z0-9-]{1,50}$");

    @Enumerated(EnumType.STRING)
    private HealthRegime regime;

    @Enumerated(EnumType.STRING)
    private AffiliateType affiliateType;

    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID payerUuid;
    private String policyNumber;

    protected Affiliation() {
    }

    public Affiliation(HealthRegime regime, AffiliateType affiliateType, UUID payerUuid, String policyNumber) {
        this.regime = DomainRules.required(regime, "affiliation.regime");
        this.affiliateType = affiliateType;
        this.payerUuid = payerUuid;
        this.policyNumber = DomainRules.matching(
                DomainRules.upper(DomainRules.optionalText(policyNumber, "affiliation.policyNumber", 50)), POLICY, "affiliation.policyNumber");
        if (regime.hasPayer()) {
            DomainRules.required(this.affiliateType, "affiliation.affiliateType");
            DomainRules.required(this.payerUuid, "affiliation.payerUuid");
        } else if (this.affiliateType != null || this.payerUuid != null || this.policyNumber != null) {
            throw new PatientException.InvalidData("affiliation", "no admite aseguradora ni póliza para pacientes sin afiliación");
        }
    }

    public static Affiliation uninsured() {
        return new Affiliation(HealthRegime.UNINSURED, null, null, null);
    }

    public HealthRegime regime() {
        return regime;
    }

    public AffiliateType affiliateType() {
        return affiliateType;
    }

    public UUID payerUuid() {
        return payerUuid;
    }

    public String policyNumber() {
        return policyNumber;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Affiliation that && regime == that.regime && affiliateType == that.affiliateType
                && Objects.equals(payerUuid, that.payerUuid) && Objects.equals(policyNumber, that.policyNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(regime, affiliateType, payerUuid, policyNumber);
    }

    @Override
    public String toString() {
        return "Affiliation[regime=" + regime + ", payerUuid=" + payerUuid + "]";
    }
}

package com.ClinicaDeYmid.patient_service.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;
import java.util.regex.Pattern;

@Embeddable
public class Affiliation {

    private static final Pattern NIT = Pattern.compile("^[0-9]{9,10}(-[0-9])?$");
    private static final Pattern POLICY = Pattern.compile("^[A-Z0-9-]{1,50}$");

    @Enumerated(EnumType.STRING)
    private HealthRegime regime;

    @Enumerated(EnumType.STRING)
    private AffiliateType affiliateType;

    private String healthProviderNit;
    private String policyNumber;

    protected Affiliation() {
    }

    public Affiliation(HealthRegime regime, AffiliateType affiliateType, String healthProviderNit, String policyNumber) {
        this.regime = DomainRules.required(regime, "affiliation.regime");
        this.affiliateType = affiliateType;
        this.healthProviderNit = DomainRules.matching(
                DomainRules.optionalText(healthProviderNit, "affiliation.healthProviderNit", 12), NIT, "affiliation.healthProviderNit");
        this.policyNumber = DomainRules.matching(
                DomainRules.upper(DomainRules.optionalText(policyNumber, "affiliation.policyNumber", 50)), POLICY, "affiliation.policyNumber");
        if (regime.hasHealthProvider()) {
            DomainRules.required(this.affiliateType, "affiliation.affiliateType");
            DomainRules.required(this.healthProviderNit, "affiliation.healthProviderNit");
        } else if (this.affiliateType != null || this.healthProviderNit != null || this.policyNumber != null) {
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

    public String healthProviderNit() {
        return healthProviderNit;
    }

    public String policyNumber() {
        return policyNumber;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Affiliation that && regime == that.regime && affiliateType == that.affiliateType
                && Objects.equals(healthProviderNit, that.healthProviderNit) && Objects.equals(policyNumber, that.policyNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(regime, affiliateType, healthProviderNit, policyNumber);
    }

    @Override
    public String toString() {
        return "Affiliation[regime=" + regime + ", healthProviderNit=" + healthProviderNit + "]";
    }
}

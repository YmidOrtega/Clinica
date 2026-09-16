package com.ClinicaDeYmid.contracting_service.domain;

import jakarta.persistence.Embeddable;

import java.util.Objects;
import java.util.regex.Pattern;

@Embeddable
public class ServiceCode {

    private static final Pattern CUPS = Pattern.compile("^[0-9]{6,8}$");
    private static final Pattern CLINIC = Pattern.compile("^[A-Z0-9][A-Z0-9.-]{1,19}$");

    private String cups;
    private String clinic;

    protected ServiceCode() {
    }

    public ServiceCode(String cups, String clinic) {
        this.cups = DomainRules.matching(DomainRules.requiredText(cups, "code.cups", 8), CUPS, "code.cups");
        this.clinic = DomainRules.matching(
                DomainRules.upper(DomainRules.requiredText(clinic, "code.clinic", 20)), CLINIC, "code.clinic");
    }

    public String cups() {
        return cups;
    }

    public String clinic() {
        return clinic;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ServiceCode that
                && Objects.equals(cups, that.cups) && Objects.equals(clinic, that.clinic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cups, clinic);
    }

    @Override
    public String toString() {
        return clinic + " (CUPS " + cups + ")";
    }
}

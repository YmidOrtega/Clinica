package com.ClinicaDeYmid.patient_service.domain;

import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.JdbcTypeCode;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;
import java.util.regex.Pattern;

@Embeddable
public class Residence {

    private static final Pattern MUNICIPALITY_CODE = Pattern.compile("^[0-9]{5}$");

    private String department;
    private String municipality;

    @JdbcTypeCode(SqlTypes.CHAR)
    private String municipalityCode;

    @Enumerated(EnumType.STRING)
    private Zone zone;

    private String address;

    protected Residence() {
    }

    public Residence(String department, String municipality, Zone zone, String address) {
        this(department, municipality, null, zone, address);
    }

    public Residence(String department, String municipality, String municipalityCode, Zone zone, String address) {
        this.department = DomainRules.requiredText(department, "residence.department", 100);
        this.municipality = DomainRules.requiredText(municipality, "residence.municipality", 100);
        this.municipalityCode = municipalityCode == null || municipalityCode.isBlank() ? null
                : DomainRules.matching(municipalityCode.strip(), MUNICIPALITY_CODE, "residence.municipalityCode");
        this.zone = DomainRules.required(zone, "residence.zone");
        this.address = DomainRules.requiredText(address, "residence.address", 255);
    }

    public String department() {
        return department;
    }

    public String municipality() {
        return municipality;
    }

    public String municipalityCode() {
        return municipalityCode;
    }

    public Zone zone() {
        return zone;
    }

    public String address() {
        return address;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Residence that && Objects.equals(department, that.department)
                && Objects.equals(municipality, that.municipality)
                && Objects.equals(municipalityCode, that.municipalityCode) && zone == that.zone && Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(department, municipality, municipalityCode, zone, address);
    }

    @Override
    public String toString() {
        return "Residence[department=" + department + ", municipality=" + municipality + "]";
    }
}

package com.ClinicaDeYmid.patient_service.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;

@Embeddable
public class Residence {

    private String department;
    private String municipality;

    @Enumerated(EnumType.STRING)
    private Zone zone;

    private String address;

    protected Residence() {
    }

    public Residence(String department, String municipality, Zone zone, String address) {
        this.department = DomainRules.requiredText(department, "residence.department", 100);
        this.municipality = DomainRules.requiredText(municipality, "residence.municipality", 100);
        this.zone = DomainRules.required(zone, "residence.zone");
        this.address = DomainRules.requiredText(address, "residence.address", 255);
    }

    public String department() {
        return department;
    }

    public String municipality() {
        return municipality;
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
                && Objects.equals(municipality, that.municipality) && zone == that.zone && Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(department, municipality, zone, address);
    }

    @Override
    public String toString() {
        return "Residence[department=" + department + ", municipality=" + municipality + "]";
    }
}

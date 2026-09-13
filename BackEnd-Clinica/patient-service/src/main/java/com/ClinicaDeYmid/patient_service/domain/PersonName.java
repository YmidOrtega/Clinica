package com.ClinicaDeYmid.patient_service.domain;

import jakarta.persistence.Embeddable;

import java.util.Objects;

@Embeddable
public class PersonName {

    private String firstNames;
    private String lastNames;

    protected PersonName() {
    }

    public PersonName(String firstNames, String lastNames) {
        this.firstNames = DomainRules.personName(firstNames, "name.firstNames", 100);
        this.lastNames = DomainRules.personName(lastNames, "name.lastNames", 100);
    }

    public String firstNames() {
        return firstNames;
    }

    public String lastNames() {
        return lastNames;
    }

    public String fullName() {
        return firstNames + " " + lastNames;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof PersonName that
                && Objects.equals(firstNames, that.firstNames) && Objects.equals(lastNames, that.lastNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(firstNames, lastNames);
    }

    @Override
    public String toString() {
        return "PersonName[redacted]";
    }
}

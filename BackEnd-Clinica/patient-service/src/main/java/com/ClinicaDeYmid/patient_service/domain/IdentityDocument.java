package com.ClinicaDeYmid.patient_service.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;
import java.util.regex.Pattern;

@Embeddable
public class IdentityDocument {

    private static final Pattern NUMBER_SEPARATORS = Pattern.compile("[\\s.-]");

    @Enumerated(EnumType.STRING)
    private DocumentType type;

    private String number;

    protected IdentityDocument() {
    }

    public IdentityDocument(DocumentType type, String number) {
        this.type = DomainRules.required(type, "document.type");
        String text = DomainRules.requiredText(number, "document.number", 30);
        this.number = DomainRules.upper(NUMBER_SEPARATORS.matcher(text).replaceAll(""));
        if (!type.acceptsNumber(this.number)) {
            throw new PatientException.InvalidData("document.number", "no tiene un formato válido para " + type.label());
        }
    }

    public DocumentType type() {
        return type;
    }

    public String number() {
        return number;
    }

    public void assertValidForAge(int years) {
        if (!type.acceptsAge(years)) {
            throw new PatientException.DocumentNotValidForAge(type);
        }
    }

    public String masked() {
        int visible = Math.min(4, number.length() - 1);
        return type + ":" + "*".repeat(number.length() - visible) + number.substring(number.length() - visible);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof IdentityDocument that && type == that.type && Objects.equals(number, that.number);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, number);
    }

    @Override
    public String toString() {
        return masked();
    }
}

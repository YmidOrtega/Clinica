package com.ClinicaDeYmid.practitioners_service.repository.entity;

import com.ClinicaDeYmid.practitioners_service.shared.DocumentType;
import com.ClinicaDeYmid.practitioners_service.shared.Rules;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;

@Embeddable
public class IdentityDocument {

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private DocumentType type;

    @Column(name = "document_number", nullable = false, length = 20)
    private String number;

    protected IdentityDocument() {
    }

    public IdentityDocument(DocumentType type, String number) {
        this.type = Rules.required(type, "document.type");
        this.number = Rules.matching(Rules.upper(Rules.requiredText(number, "document.number", 20)),
                type.format(), "document.number");
    }

    public DocumentType type() {
        return type;
    }

    public String number() {
        return number;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof IdentityDocument document
                && type == document.type && Objects.equals(number, document.number);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, number);
    }
}

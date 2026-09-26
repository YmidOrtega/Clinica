package com.ClinicaDeYmid.billing_service.application.dian;

import com.ClinicaDeYmid.billing_service.domain.Buyer;
import com.ClinicaDeYmid.billing_service.domain.DianEnvironment;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocument;
import com.ClinicaDeYmid.billing_service.domain.Issuer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ElectronicAttachment(UUID containerId, ElectronicDocument.Type type, String number, String documentKey,
                                   LocalDate issuedOn, Issuer issuer, Buyer receiver, String signedUbl,
                                   String applicationResponse, Instant validatedAt, Instant generatedAt) {

    public static ElectronicAttachment of(ElectronicDocument document, Issuer issuer, String signedUbl,
                                          String applicationResponse, Instant generatedAt) {
        boolean invoice = document.type() == ElectronicDocument.Type.INVOICE;
        Buyer receiver = invoice ? document.invoice().buyer() : document.creditNote().invoice().buyer();
        LocalDate issuedOn = invoice ? document.invoice().issuedOn() : document.creditNote().issuedOn();
        return new ElectronicAttachment(document.uuid(), document.type(), document.number(), document.documentKey(),
                issuedOn, issuer, receiver, signedUbl, applicationResponse, document.dianStatusAt(), generatedAt);
    }

    public DianEnvironment environment() {
        return issuer.environment();
    }
}

package com.ClinicaDeYmid.ai_assistant_service.support;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class InvoiceEvents {

    public static final String PAYER_NIT = "900156264";

    private InvoiceEvents() {
    }

    public static String issuedToThePayer(UUID invoice, String number, Instant at) {
        return state(invoice, number, "InvoiceIssued", at, "", "");
    }

    public static String rejectedByTheDian(UUID invoice, String number, Instant at) {
        return state(invoice, number, "InvoiceRejectedByDian", at,
                ",\"dian\":{\"signedAt\":\"%s\",\"status\":\"REJECTED\",\"statusAt\":\"%s\"}".formatted(at, at), "");
    }

    public static String uncontractedWithShortfall(UUID invoice, String number, Instant at) {
        return state(invoice, number, "InvoiceIssued", at, "", ",\"uncontractedCare\":\"EMERGENCY\",\"shareShortfall\":3500.00");
    }

    private static String state(UUID invoice, String number, String type, Instant at, String dian, String extra) {
        return """
                {"eventId":"%s","type":"%s","occurredAt":"%s","invoiceUuid":"%s","number":"%s",
                 "purpose":"SERVICES","status":"ISSUED","cufe":"%s","issuedOn":"%s",
                 "admissionNumber":"ADM-2026-000123","patientUuid":"%s",
                 "buyer":{"kind":"PAYER","reference":"%s","nit":"%s"},
                 "contractNumber":"CT-1","grossTotal":45000.00,"patientShare":0.00,"payableTotal":45000.00,
                 "creditedTotal":0.00,"balance":45000.00%s%s,"creditNotes":[],"objections":[]}"""
                .formatted(UUID.randomUUID(), type, at, invoice, number, "a".repeat(96), LocalDate.of(2026, 9, 1),
                        UUID.randomUUID(), UUID.randomUUID(), PAYER_NIT, dian, extra);
    }
}

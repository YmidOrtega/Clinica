package com.ClinicaDeYmid.ai_assistant_service.support;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class InvoiceEvents {

    public static final String PAYER_NIT = "900156264";

    private InvoiceEvents() {
    }

    public static String issuedToThePayer(UUID invoice, String number, Instant at) {
        return state(invoice, number, "InvoiceIssued", at, "", "[]");
    }

    public static String rejectedByTheDian(UUID invoice, String number, Instant at) {
        return state(invoice, number, "InvoiceRejectedByDian", at, dian("REJECTED", at), "[]");
    }

    public static String acceptedByTheDian(UUID invoice, String number, Instant at) {
        return state(invoice, number, "InvoiceAcceptedByDian", at, dian("ACCEPTED", at), "[]");
    }

    public static String uncontractedWithShortfall(UUID invoice, String number, Instant at) {
        return state(invoice, number, "InvoiceIssued", at,
                ",\"uncontractedCare\":\"EMERGENCY\",\"shareShortfall\":3500.00", "[]");
    }

    public static String filed(UUID invoice, String number, Instant at) {
        return state(invoice, number, "InvoiceFiled", at, dian("ACCEPTED", at)
                + ",\"rips\":{\"cuv\":\"" + "b".repeat(96) + "\",\"validatedAt\":\"" + at + "\"}"
                + ",\"filing\":{\"filingNumber\":\"RAD-1\",\"filedOn\":\"2026-09-10\",\"deadline\":\"2026-10-02\",\"late\":false}",
                "[]");
    }

    public static String withGloss(UUID invoice, String number, UUID gloss, String status, Instant at) {
        return state(invoice, number, "RESPONDED".equals(status) ? "InvoiceObjectionAnswered" : "InvoiceObjectionRegistered",
                at, dian("ACCEPTED", at), """
                [{"uuid":"%s","kind":"GLOSS","payerRecord":"GL-778","status":"%s","notifiedOn":"2026-09-15",
                  "extemporaneous":false,"responseDeadline":"2026-10-06","claimedAmount":8000.00}]"""
                        .formatted(gloss, status));
    }

    public static String filingAlert(UUID invoice, String number, String state) {
        return """
                {"eventId":"%s","type":"%s","occurredAt":"%s","invoiceUuid":"%s","invoiceNumber":"%s",
                 "admissionNumber":"ADM-2026-000123","payerUuid":"%s","payerName":"Nueva EPS S.A.","payerNit":"%s",
                 "payableTotal":45000.00,"issuedOn":"2026-09-01","deadline":"2026-10-02",
                 "remainingBusinessDays":%d,"state":"%s"}"""
                .formatted(UUID.randomUUID(), "OVERDUE".equals(state) ? "FilingDeadlineMissed" : "FilingDeadlineApproaching",
                        Instant.now(), invoice, number, UUID.randomUUID(), PAYER_NIT,
                        "OVERDUE".equals(state) ? -1 : 3, state);
    }

    public static String objectionAlert(UUID invoice, String number, UUID gloss, String state) {
        return """
                {"eventId":"%s","type":"%s","occurredAt":"%s","objectionUuid":"%s","kind":"GLOSS","payerRecord":"GL-778",
                 "invoiceUuid":"%s","invoiceNumber":"%s","admissionNumber":"ADM-2026-000123","payerUuid":"%s",
                 "payerName":"Nueva EPS S.A.","payerNit":"%s","claimedAmount":8000.00,"notifiedOn":"2026-09-15",
                 "responseDeadline":"2026-10-06","remainingBusinessDays":%d,"state":"%s"}"""
                .formatted(UUID.randomUUID(), "OVERDUE".equals(state) ? "ObjectionResponseMissed" : "ObjectionResponseDueSoon",
                        Instant.now(), gloss, invoice, number, UUID.randomUUID(), PAYER_NIT,
                        "OVERDUE".equals(state) ? -2 : 2, state);
    }

    private static String dian(String status, Instant at) {
        return ",\"dian\":{\"signedAt\":\"%s\",\"status\":\"%s\",\"statusAt\":\"%s\"}".formatted(at, status, at);
    }

    private static String state(UUID invoice, String number, String type, Instant at, String extra, String objections) {
        return """
                {"eventId":"%s","type":"%s","occurredAt":"%s","invoiceUuid":"%s","number":"%s",
                 "purpose":"SERVICES","status":"ISSUED","cufe":"%s","issuedOn":"%s",
                 "admissionNumber":"ADM-2026-000123","patientUuid":"%s",
                 "buyer":{"kind":"PAYER","reference":"%s","nit":"%s"},
                 "contractNumber":"CT-1","grossTotal":45000.00,"patientShare":0.00,"payableTotal":45000.00,
                 "creditedTotal":0.00,"balance":45000.00%s,"creditNotes":[],"objections":%s}"""
                .formatted(UUID.randomUUID(), type, at, invoice, number, "a".repeat(96), LocalDate.of(2026, 9, 1),
                        UUID.randomUUID(), UUID.randomUUID(), PAYER_NIT, extra, objections);
    }
}

package com.ClinicaDeYmid.ai_assistant_service.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceState(UUID invoiceUuid, String eventType, Instant occurredAt, String number, String purpose,
                           String status, LocalDate issuedOn, String admissionNumber, String buyerKind, String payerNit,
                           String contractNumber, String uncontractedCare, BigDecimal payableTotal,
                           BigDecimal creditedTotal, BigDecimal balance, BigDecimal shareShortfall, String dianStatus,
                           Instant dianStatusAt, String cuv, String filingNumber, LocalDate filedOn, String json) {
}

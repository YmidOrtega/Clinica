package com.ClinicaDeYmid.billing_service.infrastructure.events;

import com.ClinicaDeYmid.billing_service.domain.FilingDeadline;
import com.ClinicaDeYmid.billing_service.domain.Invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

record FilingDeadlineMessage(UUID eventId, String type, Instant occurredAt, String traceId, UUID invoiceUuid,
                             String invoiceNumber, String admissionNumber, UUID payerUuid, String payerName,
                             String payerNit, BigDecimal payableTotal, LocalDate issuedOn, LocalDate deadline,
                             int remainingBusinessDays, String state, String cuv) {

    static final String AGGREGATE_TYPE = "billing.filing-deadlines";
    static final String APPROACHING = "FilingDeadlineApproaching";
    static final String MISSED = "FilingDeadlineMissed";

    static FilingDeadlineMessage of(Invoice invoice, FilingDeadline deadline, String cuv, UUID eventId,
                                    Instant occurredAt, String traceId) {
        return new FilingDeadlineMessage(eventId,
                deadline.state() == FilingDeadline.State.OVERDUE ? MISSED : APPROACHING, occurredAt, traceId,
                invoice.uuid(), invoice.number(), invoice.account().admissionNumber(), invoice.buyer().reference(),
                invoice.buyer().name(), invoice.buyer().documentNumber(), invoice.payableTotal(), deadline.issuedOn(),
                deadline.deadline(), deadline.remainingBusinessDays(), deadline.state().name(), cuv);
    }
}

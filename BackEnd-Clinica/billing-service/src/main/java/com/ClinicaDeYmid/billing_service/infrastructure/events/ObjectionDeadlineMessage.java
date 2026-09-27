package com.ClinicaDeYmid.billing_service.infrastructure.events;

import com.ClinicaDeYmid.billing_service.domain.BusinessDeadline;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.PayerObjection;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

record ObjectionDeadlineMessage(UUID eventId, String type, Instant occurredAt, String traceId, UUID objectionUuid,
                                String kind, String payerRecord, UUID invoiceUuid, String invoiceNumber,
                                String admissionNumber, UUID payerUuid, String payerName, String payerNit,
                                BigDecimal claimedAmount, LocalDate notifiedOn, LocalDate responseDeadline,
                                int remainingBusinessDays, String state) {

    static final String AGGREGATE_TYPE = "billing.claim-objections";
    static final String DUE_SOON = "ObjectionResponseDueSoon";
    static final String MISSED = "ObjectionResponseMissed";

    static ObjectionDeadlineMessage of(PayerObjection objection, BusinessDeadline due, UUID eventId,
                                       Instant occurredAt, String traceId) {
        Invoice invoice = objection.invoice();
        return new ObjectionDeadlineMessage(eventId, due.state() == BusinessDeadline.State.OVERDUE ? MISSED : DUE_SOON,
                occurredAt, traceId, objection.uuid(), objection.kind().name(), objection.payerRecord(),
                invoice.uuid(), invoice.number(), invoice.account().admissionNumber(), invoice.buyer().reference(),
                invoice.buyer().name(), invoice.buyer().documentNumber(), objection.claimedAmount(),
                objection.notifiedOn(), due.deadline(), due.remainingBusinessDays(), due.state().name());
    }
}

package com.ClinicaDeYmid.billing_service.infrastructure.events;

import com.ClinicaDeYmid.billing_service.domain.Buyer;
import com.ClinicaDeYmid.billing_service.domain.CreditNote;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocument;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.InvoiceFiling;
import com.ClinicaDeYmid.billing_service.domain.Money;
import com.ClinicaDeYmid.billing_service.domain.PayerObjection;
import com.ClinicaDeYmid.billing_service.domain.RipsSubmission;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

record InvoiceStateMessage(UUID eventId, String type, Instant occurredAt, String traceId, UUID invoiceUuid,
                           String number, String purpose, String status, String cufe, LocalDate issuedOn,
                           Instant issuedAt, String admissionNumber, UUID patientUuid, BuyerPart buyer,
                           UUID contractUuid, String contractNumber, String sharedPaymentKind, BigDecimal grossTotal,
                           BigDecimal patientShare, BigDecimal payableTotal, BigDecimal creditedTotal,
                           BigDecimal balance, DianPart dian, List<CreditNotePart> creditNotes, RipsPart rips,
                           FilingPart filing, List<ObjectionPart> objections) {

    static final String AGGREGATE_TYPE = "billing.invoices";

    record BuyerPart(String kind, UUID reference, String nit) {
    }

    record DianPart(Instant signedAt, String status, Instant statusAt) {
    }

    record CreditNotePart(UUID uuid, String number, String concept, LocalDate issuedOn, BigDecimal creditedPayable,
                          String cude, String dianStatus) {
    }

    record RipsPart(String cuv, Instant validatedAt, Instant filedWithMinistryAt) {
    }

    record FilingPart(String filingNumber, LocalDate filedOn, LocalDate deadline, boolean late) {
    }

    record ObjectionPart(UUID uuid, String kind, String payerRecord, String status, LocalDate notifiedOn,
                         boolean extemporaneous, LocalDate responseDeadline, BigDecimal claimedAmount,
                         BigDecimal acceptedAmount, BigDecimal upheldAmount, String outcome) {
    }

    static InvoiceStateMessage of(String type, Invoice invoice, ElectronicDocument document,
                                  List<CreditNote> notes, Map<UUID, ElectronicDocument> noteDocuments,
                                  RipsSubmission validated, InvoiceFiling filing, List<PayerObjection> objections,
                                  UUID eventId, Instant occurredAt, String traceId) {
        Buyer buyer = invoice.buyer();
        return new InvoiceStateMessage(eventId, type, occurredAt, traceId, invoice.uuid(), invoice.number(),
                invoice.purpose().name(), invoice.status().code().name(), invoice.cufe(), invoice.issuedOn(),
                document == null ? null : document.issuedAt(), invoice.account().admissionNumber(),
                invoice.user().patientUuid(),
                new BuyerPart(buyer.kind().name(), buyer.reference(),
                        buyer.kind() == Buyer.Kind.PAYER ? buyer.documentNumber() : null),
                invoice.contractUuid(), invoice.contractNumber(),
                invoice.sharedPaymentKind() == null ? null : invoice.sharedPaymentKind().name(), invoice.grossTotal(),
                invoice.patientShare(), invoice.payableTotal(), invoice.creditedTotal(),
                Money.of(invoice.payableTotal().subtract(invoice.creditedTotal())),
                document == null || document.signedAt() == null ? null : new DianPart(document.signedAt(),
                        document.dianStatus() == null ? null : document.dianStatus().name(), document.dianStatusAt()),
                notes.stream().map(note -> {
                    ElectronicDocument noteDocument = noteDocuments.get(note.uuid());
                    return new CreditNotePart(note.uuid(), note.number(), note.concept().name(), note.issuedOn(),
                            note.creditedPayable(), note.cude(), noteDocument == null || noteDocument.dianStatus() == null
                            ? null : noteDocument.dianStatus().name());
                }).toList(),
                validated == null ? null : new RipsPart(validated.cuv(), validated.resolvedAt(), validated.filedAt()),
                filing == null ? null : new FilingPart(filing.filingNumber(), filing.filedOn(), filing.deadline(),
                        filing.late()),
                objections.stream().map(objection -> new ObjectionPart(objection.uuid(), objection.kind().name(),
                        objection.payerRecord(), objection.status().name(), objection.notifiedOn(),
                        objection.extemporaneous(), objection.responseDeadline(), objection.claimedAmount(),
                        objection.acceptedAmount(), objection.upheldAmount(),
                        objection.outcome() == null ? null : objection.outcome().name())).toList());
    }
}

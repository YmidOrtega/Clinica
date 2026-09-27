package com.ClinicaDeYmid.billing_service.application;

import java.util.UUID;

public interface InvoiceEvents {

    enum Change {
        InvoiceIssued,
        InvoiceSigned,
        InvoiceAcceptedByDian,
        InvoiceRejectedByDian,
        InvoiceCredited,
        InvoiceVoided,
        CreditNoteAcceptedByDian,
        InvoiceRipsValidated,
        InvoiceFiled,
        InvoiceFilingCorrected,
        InvoiceObjectionRegistered,
        InvoiceObjectionAnswered,
        InvoiceObjectionDecided
    }

    void invoiceChanged(UUID invoiceUuid, Change change);
}

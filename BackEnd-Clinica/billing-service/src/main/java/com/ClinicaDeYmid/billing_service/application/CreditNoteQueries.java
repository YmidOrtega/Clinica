package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.CreditNote;
import com.ClinicaDeYmid.billing_service.domain.CreditNotes;
import com.ClinicaDeYmid.billing_service.domain.DocumentFile;
import com.ClinicaDeYmid.billing_service.domain.DocumentFiles;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocument;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocuments;
import com.ClinicaDeYmid.billing_service.domain.Invoices;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CreditNoteQueries {

    private final CreditNotes notes;
    private final Invoices invoices;
    private final ElectronicDocuments documents;
    private final DocumentFiles files;

    public CreditNoteQueries(CreditNotes notes, Invoices invoices, ElectronicDocuments documents, DocumentFiles files) {
        this.notes = notes;
        this.invoices = invoices;
        this.documents = documents;
        this.files = files;
    }

    public CreditNote note(UUID uuid) {
        return notes.findByUuid(uuid).orElseThrow(BillingException.CreditNoteNotFound::new);
    }

    public ElectronicDocument electronicDocument(UUID noteUuid) {
        note(noteUuid);
        return documents.ofCreditNote(noteUuid).orElseThrow(BillingException.CreditNoteNotFound::new);
    }

    public List<CreditNote> ofInvoice(UUID invoiceUuid) {
        invoices.findByUuid(invoiceUuid).orElseThrow(BillingException.InvoiceNotFound::new);
        return notes.ofInvoice(invoiceUuid);
    }

    public DocumentFile signedOrUnsigned(UUID noteUuid) {
        ElectronicDocument document = electronicDocument(noteUuid);
        DocumentFile.Kind kind = document.signedAt() == null ? DocumentFile.Kind.UBL_UNSIGNED
                : DocumentFile.Kind.UBL_SIGNED;
        return files.find(document.uuid(), kind).orElseThrow(BillingException.CreditNoteNotFound::new);
    }
}

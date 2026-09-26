package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccount;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccounts;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.DocumentFile;
import com.ClinicaDeYmid.billing_service.domain.DocumentFiles;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocument;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocuments;
import com.ClinicaDeYmid.billing_service.domain.Invoices;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InvoiceQueries {

    private final Invoices invoices;
    private final EpisodeAccounts accounts;
    private final ElectronicDocuments documents;
    private final DocumentFiles files;

    public InvoiceQueries(Invoices invoices, EpisodeAccounts accounts, ElectronicDocuments documents,
                          DocumentFiles files) {
        this.invoices = invoices;
        this.accounts = accounts;
        this.documents = documents;
        this.files = files;
    }

    public Optional<ElectronicDocument> electronicDocument(UUID invoiceUuid) {
        invoice(invoiceUuid);
        return documents.ofInvoice(invoiceUuid);
    }

    public ElectronicDocument requireElectronicDocument(UUID invoiceUuid) {
        return electronicDocument(invoiceUuid).orElseThrow(BillingException.InvoiceNotIssued::new);
    }

    public Map<UUID, ElectronicDocument> electronicDocuments(List<Invoice> invoices) {
        Map<UUID, ElectronicDocument> found = new HashMap<>();
        invoices.forEach(invoice -> documents.ofInvoice(invoice.uuid()).ifPresent(document ->
                found.put(invoice.uuid(), document)));
        return found;
    }

    public DocumentFile signedOrUnsigned(UUID invoiceUuid) {
        ElectronicDocument document = requireElectronicDocument(invoiceUuid);
        DocumentFile.Kind kind = document.signedAt() == null ? DocumentFile.Kind.UBL_UNSIGNED
                : DocumentFile.Kind.UBL_SIGNED;
        return files.find(document.uuid(), kind).orElseThrow(BillingException.InvoiceNotFound::new);
    }

    public Invoice invoice(UUID uuid) {
        return invoices.findByUuid(uuid).orElseThrow(BillingException.InvoiceNotFound::new);
    }

    public List<Invoice> ofAccount(String admissionNumber) {
        EpisodeAccount account = accounts.findByAdmissionNumber(admissionNumber)
                .orElseThrow(BillingException.AccountNotFound::new);
        return invoices.findByAccount(account.uuid());
    }
}

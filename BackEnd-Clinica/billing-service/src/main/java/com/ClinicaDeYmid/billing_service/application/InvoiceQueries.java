package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccount;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccounts;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.InvoiceDocument;
import com.ClinicaDeYmid.billing_service.domain.InvoiceDocuments;
import com.ClinicaDeYmid.billing_service.domain.Invoices;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InvoiceQueries {

    private final Invoices invoices;
    private final EpisodeAccounts accounts;
    private final InvoiceDocuments documents;

    public InvoiceQueries(Invoices invoices, EpisodeAccounts accounts, InvoiceDocuments documents) {
        this.invoices = invoices;
        this.accounts = accounts;
        this.documents = documents;
    }

    public InvoiceDocument signedOrUnsigned(UUID invoiceUuid) {
        Invoice invoice = invoice(invoiceUuid);
        InvoiceDocument.Kind kind = invoice.signedAt() == null ? InvoiceDocument.Kind.UBL_UNSIGNED
                : InvoiceDocument.Kind.UBL_SIGNED;
        return documents.find(invoiceUuid, kind).orElseThrow(BillingException.InvoiceNotFound::new);
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

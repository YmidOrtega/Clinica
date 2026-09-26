package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.application.dian.DianSoftware;
import com.ClinicaDeYmid.billing_service.application.dian.ElectronicInvoice;
import com.ClinicaDeYmid.billing_service.application.dian.UblWriter;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.Cufe;
import com.ClinicaDeYmid.billing_service.domain.DocumentFile;
import com.ClinicaDeYmid.billing_service.domain.DocumentFiles;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocument;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocuments;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.Invoices;
import com.ClinicaDeYmid.billing_service.domain.Issuer;
import com.ClinicaDeYmid.billing_service.domain.Issuers;
import com.ClinicaDeYmid.billing_service.domain.NumberingResolution;
import com.ClinicaDeYmid.billing_service.domain.NumberingResolutions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Component
public class InvoiceIssuance {

    private final Invoices invoices;
    private final InvoiceNumbering numbering;
    private final Issuers issuers;
    private final NumberingResolutions resolutions;
    private final ElectronicDocuments documents;
    private final DocumentFiles files;
    private final UblWriter ubl;
    private final DianSoftware software;
    private final Clock clock;

    public InvoiceIssuance(Invoices invoices, InvoiceNumbering numbering, Issuers issuers,
                           NumberingResolutions resolutions, ElectronicDocuments documents, DocumentFiles files,
                           UblWriter ubl, DianSoftware software, Clock clock) {
        this.invoices = invoices;
        this.numbering = numbering;
        this.issuers = issuers;
        this.resolutions = resolutions;
        this.documents = documents;
        this.files = files;
        this.ubl = ubl;
        this.software = software;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ElectronicDocument issue(Invoice invoice) {
        invoice.issue(numbering.next(), clock);
        Issuer issuer = issuers.find().orElseThrow(BillingException.IssuerNotConfigured::new);
        NumberingResolution resolution = resolutions.findByUuid(invoice.resolutionUuid())
                .orElseThrow(BillingException.ResolutionNotFound::new);
        String cufe = Cufe.of(invoice.cufeInput(issuer, resolution));
        invoice.identify(cufe, ElectronicInvoice.qrContent(invoice, issuer, cufe));
        Invoice saved = invoices.save(invoice);
        ElectronicDocument document = documents.save(ElectronicDocument.ofInvoice(saved));
        files.save(DocumentFile.of(document, DocumentFile.Kind.UBL_UNSIGNED,
                ubl.invoice(ElectronicInvoice.of(saved, issuer, resolution, software))));
        return document;
    }
}

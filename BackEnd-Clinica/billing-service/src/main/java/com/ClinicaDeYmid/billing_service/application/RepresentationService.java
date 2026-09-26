package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.application.dian.ElectronicCreditNote;
import com.ClinicaDeYmid.billing_service.application.dian.RepresentationContent;
import com.ClinicaDeYmid.billing_service.application.dian.RepresentationRenderer;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.CreditNote;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocument;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocuments;
import com.ClinicaDeYmid.billing_service.domain.GraphicRepresentation;
import com.ClinicaDeYmid.billing_service.domain.GraphicRepresentations;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.Invoices;
import com.ClinicaDeYmid.billing_service.domain.Issuer;
import com.ClinicaDeYmid.billing_service.domain.Issuers;
import com.ClinicaDeYmid.billing_service.domain.NumberingResolution;
import com.ClinicaDeYmid.billing_service.domain.NumberingResolutions;
import com.ClinicaDeYmid.billing_service.domain.CreditNotes;
import com.ClinicaDeYmid.commons.documents.DocumentIssuer;
import com.ClinicaDeYmid.commons.documents.DocumentVerification;
import com.ClinicaDeYmid.commons.documents.Documents;
import com.ClinicaDeYmid.commons.documents.SealedDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.util.UUID;

@Service
public class RepresentationService {

    private static final Logger log = LoggerFactory.getLogger(RepresentationService.class);

    public record Requester(UUID uuid, String role, String name) {
    }

    public record Rendered(GraphicRepresentation representation, byte[] pdf) {
    }

    public record Verified(GraphicRepresentation representation, DocumentVerification verification) {
    }

    private final ElectronicDocuments documents;
    private final Invoices invoices;
    private final CreditNotes notes;
    private final Issuers issuers;
    private final NumberingResolutions resolutions;
    private final GraphicRepresentations representations;
    private final RepresentationRenderer renderer;
    private final DocumentIssuer sealer;
    private final TransactionOperations transactions;

    public RepresentationService(ElectronicDocuments documents, Invoices invoices, CreditNotes notes, Issuers issuers,
                                 NumberingResolutions resolutions, GraphicRepresentations representations,
                                 RepresentationRenderer renderer, DocumentIssuer sealer,
                                 TransactionOperations transactions) {
        this.documents = documents;
        this.invoices = invoices;
        this.notes = notes;
        this.issuers = issuers;
        this.resolutions = resolutions;
        this.representations = representations;
        this.renderer = renderer;
        this.sealer = sealer;
        this.transactions = transactions;
    }

    public Rendered ofInvoice(UUID invoiceUuid, Requester requester) {
        invoices.findByUuid(invoiceUuid).orElseThrow(BillingException.InvoiceNotFound::new);
        return render(documents.ofInvoice(invoiceUuid).orElseThrow(BillingException.InvoiceNotIssued::new), requester);
    }

    public Rendered ofCreditNote(UUID noteUuid, Requester requester) {
        notes.findByUuid(noteUuid).orElseThrow(BillingException.CreditNoteNotFound::new);
        return render(documents.ofCreditNote(noteUuid).orElseThrow(BillingException.CreditNoteNotFound::new),
                requester);
    }

    private Rendered render(ElectronicDocument document, Requester requester) {
        Invoice invoice = invoices.findByUuid(document.invoice().uuid()).orElseThrow(BillingException.InvoiceNotFound::new);
        CreditNote note = document.type() == ElectronicDocument.Type.CREDIT_NOTE
                ? notes.findByUuid(document.creditNote().uuid()).orElseThrow(BillingException.CreditNoteNotFound::new)
                : null;
        Issuer issuer = issuers.find().orElseThrow(BillingException.IssuerNotConfigured::new);
        NumberingResolution resolution = resolutions.findByUuid(invoice.resolutionUuid())
                .orElseThrow(BillingException.ResolutionNotFound::new);
        String qr = note == null ? invoice.qrContent() : ElectronicCreditNote.qrContent(note, issuer);
        byte[] pdf = renderer.render(new RepresentationContent(document, invoice, note, issuer, resolution, qr,
                requester.name(), sealer.activeKeyId()));
        SealedDocument sealed = sealer.issue(document.uuid(), requester.uuid(), requester.role(), pdf);
        GraphicRepresentation representation = new GraphicRepresentation(sealed, document.number());
        transactions.executeWithoutResult(status -> representations.add(representation));
        log.info("Graphic representation {} of {} {} sealed for {} ({} bytes)", representation.id(), document.type(),
                document.number(), requester.uuid(), pdf.length);
        return new Rendered(representation, pdf);
    }

    public GraphicRepresentation find(UUID id) {
        return representations.find(id).orElseThrow(BillingException.RepresentationNotFound::new);
    }

    public Verified verify(UUID id, byte[] pdf) {
        GraphicRepresentation representation = find(id);
        return new Verified(representation, sealer.verify(representation.document(), pdf));
    }

    public boolean authentic(String documentNumber, String sha256) {
        return representations.findByNumberAndFingerprint(documentNumber, Documents.requireSha256(sha256))
                .map(representation -> sealer.verify(representation.document(), sha256).authentic())
                .orElse(false);
    }
}

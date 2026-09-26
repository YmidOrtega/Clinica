package com.ClinicaDeYmid.billing_service.application.filing;

import com.ClinicaDeYmid.billing_service.application.DocumentAttachment;
import com.ClinicaDeYmid.billing_service.application.RepresentationService;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocument;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocuments;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.InvoiceFiling;
import com.ClinicaDeYmid.billing_service.domain.Invoices;
import com.ClinicaDeYmid.billing_service.domain.RipsSubmission;
import com.ClinicaDeYmid.billing_service.domain.RipsSubmissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class FilingPackages {

    private static final Logger log = LoggerFactory.getLogger(FilingPackages.class);

    private final Invoices invoices;
    private final ElectronicDocuments documents;
    private final DocumentAttachment attachment;
    private final RipsSubmissions submissions;
    private final RepresentationService representations;

    public FilingPackages(Invoices invoices, ElectronicDocuments documents, DocumentAttachment attachment,
                          RipsSubmissions submissions, RepresentationService representations) {
        this.invoices = invoices;
        this.documents = documents;
        this.attachment = attachment;
        this.submissions = submissions;
        this.representations = representations;
    }

    public record Package(String number, byte[] zip) {
    }

    public Package of(UUID invoiceUuid, RepresentationService.Requester requester) {
        Invoice invoice = invoices.findByUuid(invoiceUuid).orElseThrow(BillingException.InvoiceNotFound::new);
        InvoiceFiling.requireFileable(invoice);
        RipsSubmission validated = submissions.validatedOf(List.of(invoiceUuid)).stream().findFirst()
                .orElseThrow(() -> new BillingException.FilingPackageNotReady(
                        "El paquete de radicación exige el CUV del Ministerio; valida primero el RIPS"));
        ElectronicDocument document = documents.ofInvoice(invoiceUuid).orElseThrow(BillingException.InvoiceNotIssued::new);
        String container = attachment.attach(document.uuid()).content();
        byte[] pdf = representations.ofInvoice(invoiceUuid, requester).pdf();
        String number = invoice.number();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            entry(zip, number + ".xml", container.getBytes(StandardCharsets.UTF_8));
            entry(zip, number + ".pdf", pdf);
            entry(zip, number + "_RIPS.json", validated.rips().getBytes(StandardCharsets.UTF_8));
            entry(zip, number + "_CUV.json", validated.response().getBytes(StandardCharsets.UTF_8));
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        log.info("Filing package of invoice {} prepared for {}", number, requester.uuid());
        return new Package(number, bytes.toByteArray());
    }

    private static void entry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }
}

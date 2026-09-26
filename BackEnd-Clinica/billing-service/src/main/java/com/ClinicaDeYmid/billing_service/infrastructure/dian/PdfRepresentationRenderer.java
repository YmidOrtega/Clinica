package com.ClinicaDeYmid.billing_service.infrastructure.dian;

import com.ClinicaDeYmid.billing_service.application.dian.RepresentationContent;
import com.ClinicaDeYmid.billing_service.application.dian.RepresentationRenderer;
import com.ClinicaDeYmid.billing_service.domain.Buyer;
import com.ClinicaDeYmid.billing_service.domain.CreditNote;
import com.ClinicaDeYmid.billing_service.domain.CreditNoteLine;
import com.ClinicaDeYmid.billing_service.domain.DianEnvironment;
import com.ClinicaDeYmid.billing_service.domain.DianStatus;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocument;
import com.ClinicaDeYmid.billing_service.domain.HealthUser;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.InvoiceLine;
import com.ClinicaDeYmid.billing_service.domain.InvoiceStatus;
import com.ClinicaDeYmid.billing_service.domain.IssuerProfile;
import com.ClinicaDeYmid.billing_service.domain.ResolutionTerms;
import com.ClinicaDeYmid.billing_service.domain.TaxResponsibility;
import com.ClinicaDeYmid.commons.documents.PdfPages;
import com.ClinicaDeYmid.commons.documents.PdfPages.Style;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

@Component
class PdfRepresentationRenderer implements RepresentationRenderer {

    static final String PRODUCER = "billing-service";
    private static final float QR_SIZE = 110;
    private static final String ROW = "%-4s %-10s %-44s %5s %14s %14s";
    private static final Map<String, String> DOCUMENT_TYPES = Map.ofEntries(
            Map.entry("NO_IDENTIFICADO", "Sin identificar"),
            Map.entry("REGISTRO_CIVIL", "RC"),
            Map.entry("TARJETA_DE_IDENTIDAD", "TI"),
            Map.entry("CEDULA_DE_CIUDADANIA", "CC"),
            Map.entry("CEDULA_DE_EXTRANJERIA", "CE"),
            Map.entry("NIT", "NIT"),
            Map.entry("PASAPORTE", "PA"),
            Map.entry("DOCUMENTO_EXTRANJERO", "DE"),
            Map.entry("PERMISO_ESPECIAL_DE_PERMANENCIA", "PEP"),
            Map.entry("PERMISO_POR_PROTECCION_TEMPORAL", "PPT"));
    private static final Map<String, String> REGIMES = Map.of(
            "CONTRIBUTORY", "contributivo",
            "SUBSIDIZED", "subsidiado");

    private final DateTimeFormatter moment;
    private final DateTimeFormatter date = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    PdfRepresentationRenderer(@Value("${clinica.billing.time-zone:America/Bogota}") ZoneId zone) {
        this.moment = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(zone);
    }

    @Override
    public byte[] render(RepresentationContent content) {
        ElectronicDocument document = content.document();
        Invoice invoice = content.invoice();
        IssuerProfile profile = content.issuer().profile();
        String title = (content.isCreditNote() ? "Nota crédito electrónica " : "Factura electrónica de venta ")
                + document.number();
        try (PdfPages pdf = new PdfPages()) {
            pdf.line(Style.TITLE, title);
            if (content.issuer().environment() == DianEnvironment.TEST) {
                pdf.line(Style.SUBSECTION, "Documento de pruebas de habilitación: no tiene validez fiscal");
            }
            if (!content.isCreditNote() && invoice.status() instanceof InvoiceStatus.Voided voided) {
                pdf.line(Style.SUBSECTION, "ANULADA · " + voided.reason());
            }
            pdf.gap(6);

            pdf.line(Style.SECTION, profile.legalName());
            pdf.line(Style.TEXT, "NIT " + content.issuer().nit().number() + "-" + content.issuer().nit().verificationDigit()
                    + " · " + profile.addressLine() + " · " + profile.cityName() + ", " + profile.departmentName());
            pdf.line(Style.TEXT, "Tel. " + profile.phone() + " · " + profile.email());
            pdf.line(Style.TEXT, "Responsabilidades fiscales: " + TaxResponsibility.joined(profile.taxResponsibilities())
                    + " · Prestador de servicios de salud " + profile.healthProviderCode());
            if (!content.isCreditNote()) {
                ResolutionTerms terms = content.resolution().terms();
                pdf.line(Style.MUTED, "Autorización de numeración de facturación DIAN N.º " + terms.resolutionNumber()
                        + " del " + date.format(terms.issuedOn()) + ", prefijo " + terms.prefix() + ", del "
                        + terms.rangeFrom() + " al " + terms.rangeTo() + ", vigente del " + date.format(terms.validFrom())
                        + " al " + date.format(terms.validUntil()));
            }
            pdf.gap(6);

            pdf.field("Expedición", moment.format(document.issuedAt()));
            if (content.isCreditNote()) {
                CreditNote note = content.creditNote();
                pdf.field("Factura que se acredita", invoice.number() + " del " + date.format(invoice.issuedOn())
                        + " · CUFE " + invoice.cufe());
                pdf.field("Concepto", note.concept().dianCode() + " · " + note.concept().dianName());
                pdf.field("Motivo", note.reason());
            } else {
                pdf.field("Forma de pago", invoice.buyer().kind() == Buyer.Kind.PAYER
                        ? "Crédito, vence el " + date.format(invoice.issuedOn().plusDays(30)) : "Contado");
            }
            pdf.gap(6);

            pdf.line(Style.SECTION, "Adquiriente");
            Buyer buyer = invoice.buyer();
            pdf.line(Style.TEXT, buyer.name() + " · " + document(buyer.documentType()) + " " + buyer.documentNumber());
            pdf.gap(4);
            HealthUser user = invoice.user();
            pdf.line(Style.SECTION, "Usuario de los servicios de salud");
            pdf.line(Style.TEXT, user.name() + " · " + document(user.documentType()) + " " + user.documentNumber()
                    + (user.healthRegime() == null ? ""
                    : " · Régimen " + REGIMES.getOrDefault(user.healthRegime(), user.healthRegime())));
            if (invoice.contractNumber() != null) {
                pdf.field("Contrato", invoice.contractNumber());
            }
            pdf.gap(6);

            pdf.line(Style.SECTION, "Detalle");
            pdf.line(Style.MONO, ROW.formatted("#", "Código", "Descripción", "Cant.", "Valor unitario", "Total"));
            if (content.isCreditNote()) {
                for (CreditNoteLine line : content.creditNote().lines()) {
                    pdf.line(Style.MONO, row(line.position(), line.code(), line.description(), line.quantity(),
                            line.unitPrice(), line.lineTotal()));
                }
            } else {
                for (InvoiceLine line : invoice.lines()) {
                    pdf.line(Style.MONO, row(line.position(), line.code(), line.description(), line.quantity(),
                            line.unitPrice(), line.lineTotal()));
                }
            }
            pdf.gap(6);

            pdf.line(Style.SECTION, "Totales");
            if (content.isCreditNote()) {
                CreditNote note = content.creditNote();
                pdf.field("Valor acreditado", money(note.creditedGross()));
                pdf.field("Copago o cuota moderadora acreditada", money(note.creditedShare()));
                pdf.field("Total de la nota", money(note.creditedPayable()));
            } else {
                pdf.field("Subtotal", money(invoice.grossTotal()));
                for (Invoice shared : invoice.sharedPayments()) {
                    pdf.field(shared.sharedPaymentKind().label() + " facturado al paciente en la factura "
                            + shared.number(), money(shared.grossTotal()));
                }
                pdf.field("Total a pagar", money(invoice.payableTotal()));
                if (invoice.creditedTotal().signum() > 0) {
                    pdf.field("Acreditado con notas crédito", money(invoice.creditedTotal()));
                }
            }
            pdf.line(Style.MUTED, "Servicios de salud excluidos de IVA (Estatuto Tributario, art. 476).");
            pdf.gap(6);

            pdf.line(Style.SECTION, "Validación DIAN");
            pdf.line(Style.LABEL, content.isCreditNote() ? "CUDE" : "CUFE");
            pdf.line(Style.MONO, document.documentKey());
            pdf.line(Style.TEXT, validation(document));
            pdf.picture(QrCodes.image(content.qrContent()), QR_SIZE, QR_SIZE);
            pdf.line(Style.MUTED, "Consulte el documento en " + content.issuer().environment()
                    .searchUrlOf(document.documentKey()));
            pdf.line(Style.MUTED, "Facturación electrónica con software propio del emisor.");
            pdf.gap(6);
            pdf.line(Style.MUTED, "Representación gráfica generada para " + content.requestedBy()
                    + " y sellada con la clave institucional " + content.sealKeyId()
                    + ". Su huella SHA-256 queda registrada para verificarla.");
            return pdf.finish(profile.legalName() + " · " + document.number(), title, PRODUCER);
        }
    }

    private String validation(ElectronicDocument document) {
        DianStatus status = document.dianStatus();
        if (status == DianStatus.ACCEPTED) {
            return "Validado por la DIAN el " + moment.format(document.dianStatusAt());
        }
        if (status == DianStatus.REJECTED) {
            return "Rechazado por la DIAN: este documento no tiene validez hasta que se corrija y se reenvíe";
        }
        return "Pendiente de validación por la DIAN";
    }

    private static String document(String type) {
        return DOCUMENT_TYPES.getOrDefault(type, type);
    }

    private static String row(int position, String code, String description, int quantity, BigDecimal unit,
                              BigDecimal total) {
        String shortened = description.length() > 44 ? description.substring(0, 43) + "…" : description;
        return ROW.formatted(position, code, shortened, quantity, money(unit), money(total));
    }

    private static String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.of("es", "CO"));
        format.setMaximumFractionDigits(2);
        format.setMinimumFractionDigits(2);
        return format.format(amount).replace('\u00A0', ' ');
    }
}

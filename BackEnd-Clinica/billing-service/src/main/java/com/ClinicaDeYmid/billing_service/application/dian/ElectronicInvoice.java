package com.ClinicaDeYmid.billing_service.application.dian;

import com.ClinicaDeYmid.billing_service.domain.Cufe;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.InvoiceLine;
import com.ClinicaDeYmid.billing_service.domain.Issuer;
import com.ClinicaDeYmid.billing_service.domain.NumberingResolution;

import java.util.List;

public record ElectronicInvoice(Invoice invoice, Issuer issuer, NumberingResolution resolution, String softwareId,
                                String softwareSecurityCode, List<InvoiceLine> reportedLines) {

    public static ElectronicInvoice of(Invoice invoice, Issuer issuer, NumberingResolution resolution,
                                       DianSoftware configured) {
        DianSoftware software = configured.requireConfigured();
        return new ElectronicInvoice(invoice, issuer, resolution, software.softwareId(),
                Cufe.softwareSecurityCode(software.softwareId(), software.pin(), invoice.number()),
                invoice.lines().stream().filter(line -> line.lineTotal().signum() > 0).toList());
    }

    public static String qrContent(Invoice invoice, Issuer issuer, String cufe) {
        return "NumFac: " + invoice.number() + "\n"
                + "FecFac: " + invoice.issuedOn() + "\n"
                + "HorFac: " + Cufe.time(invoice.issuedTime()) + "\n"
                + "NitFac: " + issuer.nit().number() + "\n"
                + "DocAdq: " + Cufe.withoutVerificationDigit(invoice.buyer().documentNumber()) + "\n"
                + "ValFac: " + Cufe.amount(invoice.grossTotal()) + "\n"
                + "ValIva: 0.00\n"
                + "ValOtroIm: 0.00\n"
                + "ValTolFac: " + Cufe.amount(invoice.payableTotal()) + "\n"
                + "CUFE: " + cufe + "\n"
                + "QRCode: " + issuer.environment().searchUrlOf(cufe);
    }
}

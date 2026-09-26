package com.ClinicaDeYmid.billing_service.application.dian;

import com.ClinicaDeYmid.billing_service.domain.CreditNote;
import com.ClinicaDeYmid.billing_service.domain.Cufe;
import com.ClinicaDeYmid.billing_service.domain.Issuer;

public record ElectronicCreditNote(CreditNote note, Issuer issuer, String softwareId, String softwareSecurityCode) {

    public static ElectronicCreditNote of(CreditNote note, Issuer issuer, DianSoftware configured) {
        DianSoftware software = configured.requireConfigured();
        return new ElectronicCreditNote(note, issuer, software.softwareId(),
                Cufe.softwareSecurityCode(software.softwareId(), software.pin(), note.number()));
    }

    public static String qrContent(CreditNote note, Issuer issuer) {
        return "NumNC: " + note.number() + "\n"
                + "FecNC: " + note.issuedOn() + "\n"
                + "HorNC: " + Cufe.time(note.issuedTime()) + "\n"
                + "NitFac: " + issuer.nit().number() + "\n"
                + "DocAdq: " + Cufe.withoutVerificationDigit(note.invoice().buyer().documentNumber()) + "\n"
                + "ValNC: " + Cufe.amount(note.creditedGross()) + "\n"
                + "ValIva: 0.00\n"
                + "ValOtroIm: 0.00\n"
                + "ValTolNC: " + Cufe.amount(note.creditedPayable()) + "\n"
                + "CUDE: " + note.cude() + "\n"
                + "QRCode: " + issuer.environment().searchUrlOf(note.cude());
    }
}

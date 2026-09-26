package com.ClinicaDeYmid.billing_service.application.dian;

import com.ClinicaDeYmid.billing_service.domain.CreditNote;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocument;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.Issuer;
import com.ClinicaDeYmid.billing_service.domain.NumberingResolution;

public record RepresentationContent(ElectronicDocument document, Invoice invoice, CreditNote creditNote,
                                    Issuer issuer, NumberingResolution resolution, String qrContent,
                                    String requestedBy, String sealKeyId) {

    public boolean isCreditNote() {
        return creditNote != null;
    }
}

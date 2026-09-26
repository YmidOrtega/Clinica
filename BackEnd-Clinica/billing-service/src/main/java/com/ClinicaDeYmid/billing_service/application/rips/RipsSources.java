package com.ClinicaDeYmid.billing_service.application.rips;

import com.ClinicaDeYmid.billing_service.application.clinical.CareRecord;
import com.ClinicaDeYmid.billing_service.application.context.EpisodeDetails;
import com.ClinicaDeYmid.billing_service.application.context.PatientDetails;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccount;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.Issuer;

import java.time.ZoneId;

public record RipsSources(Invoice invoice, Issuer issuer, EpisodeAccount account, EpisodeDetails episode,
                          PatientDetails patient, Professional professional, CareRecord care, ZoneId zone) {

    public record Professional(String documentType, String documentNumber) {
    }
}

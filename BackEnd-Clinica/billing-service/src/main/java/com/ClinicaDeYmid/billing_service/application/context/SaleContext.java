package com.ClinicaDeYmid.billing_service.application.context;

import com.ClinicaDeYmid.billing_service.domain.EpisodeAccount;

import java.util.List;

public record SaleContext(
        EpisodeAccount account,
        EpisodeDetails episode,
        PatientDetails patient,
        PayerDetails payer,
        List<String> warnings) {

    public SaleContext {
        warnings = List.copyOf(warnings);
    }

    public boolean complete() {
        return warnings.isEmpty();
    }
}

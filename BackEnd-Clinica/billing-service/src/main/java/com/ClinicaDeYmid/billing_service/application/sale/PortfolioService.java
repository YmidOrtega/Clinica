package com.ClinicaDeYmid.billing_service.application.sale;

import com.ClinicaDeYmid.billing_service.domain.ChargedService;

import java.util.UUID;

public record PortfolioService(UUID uuid, String cupsCode, String clinicCode, String name, String category,
                               boolean offered) {

    public ChargedService charged() {
        return new ChargedService(uuid, cupsCode, clinicCode, name, category);
    }
}

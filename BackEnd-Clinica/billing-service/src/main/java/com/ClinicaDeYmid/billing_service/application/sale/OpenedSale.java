package com.ClinicaDeYmid.billing_service.application.sale;

import com.ClinicaDeYmid.billing_service.domain.Sale;

import java.util.List;

public record OpenedSale(Sale sale, List<String> notes) {

    public OpenedSale {
        notes = List.copyOf(notes);
    }
}

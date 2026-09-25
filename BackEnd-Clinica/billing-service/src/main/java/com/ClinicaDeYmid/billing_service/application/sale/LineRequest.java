package com.ClinicaDeYmid.billing_service.application.sale;

import com.ClinicaDeYmid.billing_service.domain.BillingException;

import java.time.LocalDate;
import java.util.UUID;

public record LineRequest(UUID portfolioItemUuid, String cupsCode, int quantity, LocalDate serviceDate) {

    public LineRequest {
        if ((portfolioItemUuid == null) == (cupsCode == null || cupsCode.isBlank())) {
            throw new BillingException.InvalidData("service", "indica el servicio con portfolioItemUuid o con cupsCode, "
                    + "no con ambos");
        }
    }
}

package com.ClinicaDeYmid.billing_service.application.sale;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PriceQuotes {

    QuoteLookup quote(UUID contractUuid, LocalDate on, List<Requested> services);

    QuoteLookup surgicalQuote(UUID contractUuid, LocalDate on, List<RequestedProcedure> procedures);

    record Requested(String cupsCode, int quantity) {
    }

    record RequestedProcedure(String cupsCode, String route) {
    }
}

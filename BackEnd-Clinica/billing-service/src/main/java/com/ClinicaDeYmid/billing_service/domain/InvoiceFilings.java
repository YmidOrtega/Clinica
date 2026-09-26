package com.ClinicaDeYmid.billing_service.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceFilings {

    InvoiceFiling save(InvoiceFiling filing);

    Optional<InvoiceFiling> ofInvoice(UUID invoiceUuid);

    List<Invoice> awaitingFiling(UUID payerUuid, int limit);
}

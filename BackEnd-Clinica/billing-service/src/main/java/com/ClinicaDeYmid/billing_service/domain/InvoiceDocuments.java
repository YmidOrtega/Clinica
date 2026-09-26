package com.ClinicaDeYmid.billing_service.domain;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceDocuments {

    InvoiceDocument save(InvoiceDocument document);

    Optional<InvoiceDocument> find(UUID invoiceUuid, InvoiceDocument.Kind kind);
}

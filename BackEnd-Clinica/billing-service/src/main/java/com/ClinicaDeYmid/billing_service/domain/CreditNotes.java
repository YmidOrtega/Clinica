package com.ClinicaDeYmid.billing_service.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditNotes {

    CreditNote save(CreditNote note);

    Optional<CreditNote> findByUuid(UUID uuid);

    List<CreditNote> ofInvoice(UUID invoiceUuid);
}

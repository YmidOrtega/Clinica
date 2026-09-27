package com.ClinicaDeYmid.billing_service.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayerObjections {

    PayerObjection save(PayerObjection objection);

    Optional<PayerObjection> findByUuid(UUID uuid);

    List<PayerObjection> ofInvoice(UUID invoiceUuid);

    List<PayerObjection> awaitingResponse(UUID payerUuid, int limit);
}

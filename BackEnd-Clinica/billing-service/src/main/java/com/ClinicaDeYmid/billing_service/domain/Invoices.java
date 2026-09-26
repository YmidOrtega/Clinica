package com.ClinicaDeYmid.billing_service.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Invoices {

    Invoice save(Invoice invoice);

    Optional<Invoice> findByUuid(UUID uuid);

    List<Invoice> findByAccount(UUID accountUuid);

    boolean liveFor(UUID accountUuid, UUID saleUuid);
}

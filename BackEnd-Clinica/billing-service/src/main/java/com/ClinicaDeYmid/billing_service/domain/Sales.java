package com.ClinicaDeYmid.billing_service.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Sales {

    Sale save(Sale sale);

    Optional<Sale> findByUuid(UUID uuid);

    List<Sale> findByAccount(UUID accountUuid);

    int countByAccount(UUID accountUuid);
}

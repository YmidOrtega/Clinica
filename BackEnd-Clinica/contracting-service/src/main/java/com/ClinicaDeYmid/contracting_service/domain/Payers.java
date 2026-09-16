package com.ClinicaDeYmid.contracting_service.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Payers {

    Payer save(Payer payer);

    Optional<Payer> findByUuid(UUID uuid);

    Optional<Payer> findByNit(Nit nit);

    boolean existsByNit(Nit nit);

    List<Payer> searchBySocialReason(String prefix, int page, int size);

    long countBySocialReason(String prefix);
}

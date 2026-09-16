package com.ClinicaDeYmid.contracting_service.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioItems {

    PortfolioItem save(PortfolioItem item);

    Optional<PortfolioItem> findByUuid(UUID uuid);

    Optional<PortfolioItem> findByClinicCode(String clinicCode);

    List<PortfolioItem> findByCupsCode(String cupsCode);

    List<PortfolioItem> searchByName(String prefix, int page, int size);

    long countByName(String prefix);
}

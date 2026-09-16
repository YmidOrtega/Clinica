package com.ClinicaDeYmid.contracting_service.infrastructure.persistence;

import com.ClinicaDeYmid.contracting_service.domain.PortfolioItem;
import com.ClinicaDeYmid.contracting_service.domain.PortfolioItems;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaPortfolioItems implements PortfolioItems {

    private final PortfolioItemJpaRepository repository;

    JpaPortfolioItems(PortfolioItemJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public PortfolioItem save(PortfolioItem item) {
        return repository.saveAndFlush(item);
    }

    @Override
    public Optional<PortfolioItem> findByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    @Override
    public Optional<PortfolioItem> findByClinicCode(String clinicCode) {
        return repository.findByClinicCode(clinicCode);
    }

    @Override
    public List<PortfolioItem> findByCupsCode(String cupsCode) {
        return repository.findByCupsCode(cupsCode);
    }

    @Override
    public List<PortfolioItem> searchByName(String prefix, int page, int size) {
        return repository.searchByName(prefix + "%", PageRequest.of(page, size)).getContent();
    }

    @Override
    public long countByName(String prefix) {
        return repository.searchByName(prefix + "%", PageRequest.of(0, 1)).getTotalElements();
    }
}

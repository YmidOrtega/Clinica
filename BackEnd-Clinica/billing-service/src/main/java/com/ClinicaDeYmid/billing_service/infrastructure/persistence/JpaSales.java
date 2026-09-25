package com.ClinicaDeYmid.billing_service.infrastructure.persistence;

import com.ClinicaDeYmid.billing_service.domain.Sale;
import com.ClinicaDeYmid.billing_service.domain.Sales;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaSales implements Sales {

    private final SaleJpaRepository repository;

    JpaSales(SaleJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Sale save(Sale sale) {
        return repository.saveAndFlush(sale);
    }

    @Override
    public Optional<Sale> findByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    @Override
    public List<Sale> findByAccount(UUID accountUuid) {
        return repository.findByAccount(accountUuid);
    }

    @Override
    public int countByAccount(UUID accountUuid) {
        return repository.countByAccount(accountUuid);
    }
}

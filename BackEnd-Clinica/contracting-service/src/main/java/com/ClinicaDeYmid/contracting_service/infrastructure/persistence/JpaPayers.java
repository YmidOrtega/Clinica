package com.ClinicaDeYmid.contracting_service.infrastructure.persistence;

import com.ClinicaDeYmid.contracting_service.domain.Nit;
import com.ClinicaDeYmid.contracting_service.domain.Payer;
import com.ClinicaDeYmid.contracting_service.domain.Payers;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaPayers implements Payers {

    private final PayerJpaRepository repository;

    JpaPayers(PayerJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Payer save(Payer payer) {
        return repository.saveAndFlush(payer);
    }

    @Override
    public Optional<Payer> findByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    @Override
    public Optional<Payer> findByNit(Nit nit) {
        return repository.findByNitNumber(nit.number());
    }

    @Override
    public boolean existsByNit(Nit nit) {
        return repository.existsByNitNumber(nit.number());
    }

    @Override
    public List<Payer> searchBySocialReason(String prefix, int page, int size) {
        return repository.searchBySocialReason(prefix + "%", PageRequest.of(page, size)).getContent();
    }

    @Override
    public long countBySocialReason(String prefix) {
        return repository.searchBySocialReason(prefix + "%", PageRequest.of(0, 1)).getTotalElements();
    }
}

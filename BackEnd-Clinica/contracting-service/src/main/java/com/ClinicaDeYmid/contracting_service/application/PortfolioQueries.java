package com.ClinicaDeYmid.contracting_service.application;

import com.ClinicaDeYmid.contracting_service.domain.ContractingException;
import com.ClinicaDeYmid.contracting_service.domain.PortfolioItem;
import com.ClinicaDeYmid.contracting_service.domain.PortfolioItems;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PortfolioQueries {

    private final PortfolioItems items;

    public PortfolioQueries(PortfolioItems items) {
        this.items = items;
    }

    public PortfolioItem get(UUID uuid) {
        return items.findByUuid(uuid).orElseThrow(ContractingException.PortfolioItemNotFound::new);
    }

    public List<PortfolioItem> findByClinicCode(String clinicCode) {
        return items.findByClinicCode(clinicCode).stream().toList();
    }

    public List<PortfolioItem> findByCupsCode(String cupsCode) {
        return items.findByCupsCode(cupsCode);
    }

    public Page searchByName(String prefix, int page, int size) {
        return new Page(items.searchByName(prefix, page, size), items.countByName(prefix));
    }

    public record Page(List<PortfolioItem> matches, long total) {
    }
}

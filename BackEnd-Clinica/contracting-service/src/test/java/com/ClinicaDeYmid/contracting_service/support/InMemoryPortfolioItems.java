package com.ClinicaDeYmid.contracting_service.support;

import com.ClinicaDeYmid.contracting_service.domain.PortfolioItem;
import com.ClinicaDeYmid.contracting_service.domain.PortfolioItems;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryPortfolioItems implements PortfolioItems {

    private final Map<UUID, PortfolioItem> stored = new LinkedHashMap<>();

    @Override
    public PortfolioItem save(PortfolioItem item) {
        stored.put(item.uuid(), item);
        return item;
    }

    @Override
    public Optional<PortfolioItem> findByUuid(UUID uuid) {
        return Optional.ofNullable(stored.get(uuid));
    }

    @Override
    public Optional<PortfolioItem> findByClinicCode(String clinicCode) {
        return stored.values().stream().filter(item -> item.code().clinic().equals(clinicCode)).findFirst();
    }

    @Override
    public List<PortfolioItem> findByCupsCode(String cupsCode) {
        return stored.values().stream().filter(item -> item.code().cups().equals(cupsCode)).toList();
    }

    @Override
    public List<PortfolioItem> searchByName(String prefix, int page, int size) {
        List<PortfolioItem> matches = new ArrayList<>(matching(prefix));
        matches.sort(Comparator.comparing(PortfolioItem::name));
        int from = Math.min(page * size, matches.size());
        return matches.subList(from, Math.min(from + size, matches.size()));
    }

    @Override
    public long countByName(String prefix) {
        return matching(prefix).size();
    }

    private List<PortfolioItem> matching(String prefix) {
        return stored.values().stream()
                .filter(item -> item.name().toLowerCase().startsWith(prefix.toLowerCase()))
                .toList();
    }
}

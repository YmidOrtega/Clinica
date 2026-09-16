package com.ClinicaDeYmid.contracting_service.support;

import com.ClinicaDeYmid.contracting_service.domain.Nit;
import com.ClinicaDeYmid.contracting_service.domain.Payer;
import com.ClinicaDeYmid.contracting_service.domain.Payers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryPayers implements Payers {

    private final Map<UUID, Payer> stored = new LinkedHashMap<>();

    @Override
    public Payer save(Payer payer) {
        stored.put(payer.uuid(), payer);
        return payer;
    }

    @Override
    public Optional<Payer> findByUuid(UUID uuid) {
        return Optional.ofNullable(stored.get(uuid));
    }

    @Override
    public Optional<Payer> findByNit(Nit nit) {
        return stored.values().stream().filter(payer -> payer.nit().equals(nit)).findFirst();
    }

    @Override
    public boolean existsByNit(Nit nit) {
        return findByNit(nit).isPresent();
    }

    @Override
    public List<Payer> searchBySocialReason(String prefix, int page, int size) {
        List<Payer> matches = new ArrayList<>(matching(prefix));
        matches.sort(Comparator.comparing(Payer::socialReason));
        int from = Math.min(page * size, matches.size());
        return matches.subList(from, Math.min(from + size, matches.size()));
    }

    @Override
    public long countBySocialReason(String prefix) {
        return matching(prefix).size();
    }

    private List<Payer> matching(String prefix) {
        return stored.values().stream()
                .filter(payer -> payer.socialReason().toLowerCase().startsWith(prefix.toLowerCase()))
                .toList();
    }
}

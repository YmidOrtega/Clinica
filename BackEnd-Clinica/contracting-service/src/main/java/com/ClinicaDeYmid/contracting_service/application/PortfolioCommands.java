package com.ClinicaDeYmid.contracting_service.application;

import com.ClinicaDeYmid.commons.web.EntityTags;
import com.ClinicaDeYmid.contracting_service.domain.ContractingException;
import com.ClinicaDeYmid.contracting_service.domain.PortfolioItem;
import com.ClinicaDeYmid.contracting_service.domain.PortfolioItems;
import com.ClinicaDeYmid.contracting_service.domain.ServiceCategory;
import com.ClinicaDeYmid.contracting_service.domain.ServiceCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class PortfolioCommands {

    private static final Logger log = LoggerFactory.getLogger(PortfolioCommands.class);

    private final PortfolioItems items;
    private final TransactionOperations transactions;
    private final Clock clock;

    public PortfolioCommands(PortfolioItems items, TransactionOperations transactions, Clock clock) {
        this.items = items;
        this.transactions = transactions;
        this.clock = clock;
    }

    public PortfolioItem offer(ServiceCode code, String name, ServiceCategory category) {
        return transactions.execute(status -> {
            if (items.findByClinicCode(code.clinic()).isPresent()) {
                throw new ContractingException.ClinicCodeAlreadyUsed();
            }
            PortfolioItem offered = items.save(PortfolioItem.offer(code, name, category));
            log.info("Portfolio item offered: uuid={} clinicCode={}", offered.uuid(), code.clinic());
            return offered;
        });
    }

    public PortfolioItem describe(UUID uuid, long expectedVersion, ServiceCode code, String name, ServiceCategory category) {
        return modify(uuid, expectedVersion, item -> {
            items.findByClinicCode(code.clinic())
                    .filter(other -> !other.uuid().equals(uuid))
                    .ifPresent(other -> {
                        throw new ContractingException.ClinicCodeAlreadyUsed();
                    });
            item.describe(code, name, category);
        });
    }

    public PortfolioItem stopOffering(UUID uuid, long expectedVersion, String reason) {
        return modify(uuid, expectedVersion, item -> item.stopOffering(reason, clock));
    }

    public PortfolioItem offerAgain(UUID uuid, long expectedVersion) {
        return modify(uuid, expectedVersion, PortfolioItem::offerAgain);
    }

    public Import importAll(List<Entry> entries) {
        return transactions.execute(status -> {
            int created = 0;
            int updated = 0;
            int unchanged = 0;
            for (Entry entry : entries) {
                PortfolioItem existing = items.findByClinicCode(entry.code().clinic()).orElse(null);
                if (existing == null) {
                    items.save(PortfolioItem.offer(entry.code(), entry.name(), entry.category()));
                    created++;
                } else if (existing.describe(entry.code(), entry.name(), entry.category())) {
                    items.save(existing);
                    updated++;
                } else {
                    unchanged++;
                }
            }
            log.info("Portfolio import: created={} updated={} unchanged={}", created, updated, unchanged);
            return new Import(created, updated, unchanged);
        });
    }

    private PortfolioItem modify(UUID uuid, long expectedVersion, Consumer<PortfolioItem> change) {
        return transactions.execute(status -> {
            PortfolioItem item = items.findByUuid(uuid).orElseThrow(ContractingException.PortfolioItemNotFound::new);
            if (item.version() != expectedVersion) {
                throw new EntityTags.StaleVersion();
            }
            change.accept(item);
            return items.save(item);
        });
    }

    public record Entry(ServiceCode code, String name, ServiceCategory category) {
    }

    public record Import(int created, int updated, int unchanged) {
    }
}

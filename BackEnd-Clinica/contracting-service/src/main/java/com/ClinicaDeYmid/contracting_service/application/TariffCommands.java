package com.ClinicaDeYmid.contracting_service.application;

import com.ClinicaDeYmid.commons.web.EntityTags;
import com.ClinicaDeYmid.contracting_service.domain.ContractingException;
import com.ClinicaDeYmid.contracting_service.domain.PriceUnit;
import com.ClinicaDeYmid.contracting_service.domain.SurgicalBasis;
import com.ClinicaDeYmid.contracting_service.domain.SurgicalComponentRule;
import com.ClinicaDeYmid.contracting_service.domain.SurgicalRange;
import com.ClinicaDeYmid.contracting_service.domain.SurgicalRuleSet;
import com.ClinicaDeYmid.contracting_service.domain.TariffItem;
import com.ClinicaDeYmid.contracting_service.domain.TariffManual;
import com.ClinicaDeYmid.contracting_service.domain.TariffManualVersion;
import com.ClinicaDeYmid.contracting_service.domain.TariffManuals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TariffCommands {

    private static final Logger log = LoggerFactory.getLogger(TariffCommands.class);

    private static final char FIELD_SEPARATOR = 31;
    private static final char RECORD_SEPARATOR = 30;

    private final TariffManuals manuals;
    private final ContractingEventOutbox outbox;
    private final TransactionOperations transactions;
    private final Clock clock;

    public TariffCommands(TariffManuals manuals, ContractingEventOutbox outbox, TransactionOperations transactions,
                          Clock clock) {
        this.manuals = manuals;
        this.outbox = outbox;
        this.transactions = transactions;
        this.clock = clock;
    }

    public TariffManual registerManual(String code, String name, PriceUnit unit) {
        return transactions.execute(status -> {
            TariffManual manual = TariffManual.of(code, name, unit);
            if (manuals.findByCode(manual.code()).isPresent()) {
                throw new ContractingException.ManualCodeAlreadyUsed();
            }
            log.info("Tariff manual registered: code={} unit={}", manual.code(), unit);
            return manuals.save(manual);
        });
    }

    public TariffManualVersion draftVersion(UUID manualUuid, String label, BigDecimal unitValue, LocalDate validFrom) {
        return transactions.execute(status -> {
            TariffManual manual = manuals.findByUuid(manualUuid).orElseThrow(ContractingException.TariffManualNotFound::new);
            if (manuals.versionsOf(manualUuid).stream().anyMatch(existing -> existing.label().equalsIgnoreCase(label))) {
                throw new ContractingException.VersionLabelAlreadyUsed();
            }
            manuals.activeVersionOf(manualUuid)
                    .filter(current -> !current.validFrom().isBefore(validFrom))
                    .ifPresent(current -> {
                        throw new ContractingException.InvalidData("validFrom",
                                "debe ser posterior al inicio de la versión vigente (" + current.validFrom() + ")");
                    });
            return manuals.save(TariffManualVersion.draft(manual, label, unitValue, validFrom));
        });
    }

    public Load load(UUID versionUuid, List<Entry> entries) {
        return transactions.execute(status -> {
            TariffManualVersion version = version(versionUuid);
            String checksum = checksumOf(entries);
            if (checksum.equals(version.sourceChecksum())) {
                return new Load(version, 0, true);
            }
            version.requireEditable();
            if (version.itemCount() > 0) {
                throw new ContractingException.TariffVersionNotEditable("ya tiene tarifas cargadas");
            }
            List<TariffItem> items = entries.stream()
                    .map(entry -> TariffItem.of(version, entry.cupsCode(), entry.description(), entry.value(),
                            entry.surgicalBasis()))
                    .toList();
            manuals.saveItems(items);
            version.loaded(checksum, items.size(), (int) items.stream().filter(TariffItem::surgical).count());
            log.info("Tariff version loaded: uuid={} items={} checksum={}", versionUuid, items.size(), checksum);
            return new Load(manuals.save(version), items.size(), false);
        });
    }

    public TariffManualVersion activate(UUID versionUuid, long expectedVersion) {
        return transactions.execute(status -> {
            TariffManualVersion version = version(versionUuid);
            if (version.version() != expectedVersion) {
                throw new EntityTags.StaleVersion();
            }
            manuals.activeVersionOf(version.manual().uuid())
                    .filter(current -> !current.uuid().equals(versionUuid))
                    .ifPresent(current -> {
                        current.retire(clock);
                        outbox.tariffVersionChanged(manuals.save(current), "TariffVersionRetired");
                    });
            version.activate(clock);
            log.info("Tariff version activated: manual={} label={}", version.manual().code(), version.label());
            TariffManualVersion published = manuals.save(version);
            outbox.tariffVersionChanged(published, "TariffVersionPublished");
            return published;
        });
    }

    private TariffManualVersion version(UUID versionUuid) {
        return manuals.findVersionByUuid(versionUuid).orElseThrow(ContractingException.TariffVersionNotFound::new);
    }

    static String checksumOf(List<Entry> entries) {
        StringBuilder canonical = new StringBuilder();
        entries.stream()
                .sorted(Comparator.comparing(Entry::cupsCode))
                .forEach(entry -> canonical.append(entry.cupsCode()).append(FIELD_SEPARATOR)
                        .append(entry.description()).append(FIELD_SEPARATOR)
                        .append(entry.value().stripTrailingZeros().toPlainString())
                        .append(entry.surgicalBasis() == null ? ""
                                : FIELD_SEPARATOR + entry.surgicalBasis().stripTrailingZeros().toPlainString())
                        .append(RECORD_SEPARATOR));
        return sha256(canonical.toString());
    }

    private static String sha256(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record Entry(String cupsCode, String description, BigDecimal value, BigDecimal surgicalBasis) {

        public Entry(String cupsCode, String description, BigDecimal value) {
            this(cupsCode, description, value, null);
        }
    }

    public SurgicalRuleSet loadSurgicalRules(UUID versionUuid, SurgicalBasis basis,
                                             List<SurgicalComponentRule.Definition> definitions, String actor) {
        return transactions.execute(status -> {
            TariffManualVersion version = version(versionUuid);
            String checksum = surgicalChecksumOf(basis, definitions);
            Optional<SurgicalRuleSet> loaded = manuals.surgicalRulesOf(versionUuid);
            if (loaded.isPresent()) {
                if (loaded.get().checksum().equals(checksum)) {
                    return loaded.get();
                }
                throw new ContractingException.SurgicalRulesAlreadyLoaded();
            }
            SurgicalRuleSet rules = manuals.save(SurgicalRuleSet.load(version, basis, definitions, checksum, actor, clock));
            version.surgicalRulesLoaded(checksum);
            manuals.save(version);
            log.info("Surgical rules loaded: version={} basis={} components={}", versionUuid, basis, definitions.size());
            return rules;
        });
    }

    static String surgicalChecksumOf(SurgicalBasis basis, List<SurgicalComponentRule.Definition> definitions) {
        StringBuilder canonical = new StringBuilder(String.valueOf(basis)).append(RECORD_SEPARATOR);
        definitions.stream()
                .sorted(Comparator.comparing(definition -> String.valueOf(definition.component())))
                .forEach(definition -> {
                    canonical.append(definition.component()).append(FIELD_SEPARATOR).append(definition.mode())
                            .append(FIELD_SEPARATOR).append(plain(definition.rate()))
                            .append(FIELD_SEPARATOR).append(plain(definition.minimumBasis()))
                            .append(FIELD_SEPARATOR).append(plain(definition.sameRoutePercent()))
                            .append(FIELD_SEPARATOR).append(plain(definition.differentRoutePercent()));
                    if (definition.ranges() != null) {
                        definition.ranges().stream()
                                .sorted(Comparator.comparing(SurgicalRange::from, Comparator.nullsFirst(Comparator.naturalOrder())))
                                .forEach(range -> canonical.append(FIELD_SEPARATOR).append(plain(range.from()))
                                        .append('-').append(plain(range.to())).append('=').append(plain(range.value())));
                    }
                    canonical.append(RECORD_SEPARATOR);
                });
        return sha256(canonical.toString());
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    public record Load(TariffManualVersion version, int loaded, boolean alreadyLoaded) {
    }
}

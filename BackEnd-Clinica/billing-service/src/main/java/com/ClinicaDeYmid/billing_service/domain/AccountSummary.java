package com.ClinicaDeYmid.billing_service.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record AccountSummary(EpisodeAccount account, List<Unit> units, BigDecimal total, BigDecimal patientShare,
                             BigDecimal payerShare) {

    public enum UnitKind {
        ACCOUNT,
        SALE
    }

    public enum ShareSource {
        COPAYMENT,
        PRIVATE,
        ADJUSTED
    }

    public record Unit(UnitKind kind, UUID saleUuid, List<Sale> sales, boolean ready, String notReadyReason,
                       BigDecimal linesTotal, List<PackageCharge> packages, List<PackageCharge> duplicatePackages,
                       BigDecimal total, BigDecimal patientShare, ShareSource shareSource, List<Copayment> copayments,
                       PatientShareAdjustment adjustment, BigDecimal payerShare) {
    }

    public static AccountSummary of(EpisodeAccount account, List<Sale> sales, boolean covered, List<Copayment> copayments,
                                    List<PatientShareAdjustment> adjustments) {
        List<Sale> ordered = sales.stream().sorted(Comparator.comparing(Sale::number)).toList();
        List<Sale> confirmed = ordered.stream().filter(sale -> sale.status() instanceof SaleStatus.Confirmed).toList();
        boolean drafts = ordered.stream().anyMatch(sale -> sale.status() instanceof SaleStatus.Draft);
        List<Unit> units = new ArrayList<>();
        Set<UUID> chargedPackages = new HashSet<>();
        Set<UUID> usedAuthorizations = new HashSet<>();
        if (!(account.status() instanceof AccountStatus.Voided)) {
            if (account.kind() == AdmissionKind.OUTPATIENT) {
                for (Sale sale : confirmed) {
                    units.add(unit(UnitKind.SALE, sale.uuid(), List.of(sale), true, null, covered, copayments,
                            adjustments, chargedPackages, usedAuthorizations));
                }
            } else if (!confirmed.isEmpty() || drafts) {
                String notReady = !(account.status() instanceof AccountStatus.Frozen)
                        ? "El paciente aún no tiene egreso"
                        : drafts ? "Hay ventas en borrador por confirmar o anular" : null;
                units.add(unit(UnitKind.ACCOUNT, null, confirmed, notReady == null, notReady, covered, copayments,
                        adjustments, chargedPackages, usedAuthorizations));
            }
        }
        BigDecimal total = units.stream().map(Unit::total).reduce(Money.ZERO, BigDecimal::add);
        BigDecimal patient = units.stream().map(Unit::patientShare).reduce(Money.ZERO, BigDecimal::add);
        return new AccountSummary(account, units, total, patient, total.subtract(patient));
    }

    private static Unit unit(UnitKind kind, UUID saleUuid, List<Sale> sales, boolean ready, String notReady,
                             boolean covered, List<Copayment> copayments, List<PatientShareAdjustment> adjustments,
                             Set<UUID> chargedPackages, Set<UUID> usedAuthorizations) {
        BigDecimal lines = Money.ZERO;
        List<PackageCharge> packages = new ArrayList<>();
        List<PackageCharge> duplicates = new ArrayList<>();
        for (Sale sale : sales) {
            lines = lines.add(sale.settlement().map(Sale.Settlement::linesTotal).orElse(Money.ZERO));
            for (SalePackage applied : sale.packages()) {
                PackageCharge charge = applied.charge();
                if (chargedPackages.add(charge.packageUuid())) {
                    packages.add(charge);
                } else {
                    duplicates.add(charge);
                }
            }
        }
        BigDecimal total = Money.of(lines.add(packages.stream().map(PackageCharge::price).reduce(Money.ZERO, BigDecimal::add)));
        Optional<PatientShareAdjustment> adjustment = adjustments.stream()
                .filter(candidate -> kind == UnitKind.SALE ? saleUuid.equals(candidate.saleUuid()) : candidate.saleUuid() == null)
                .max(Comparator.comparing(PatientShareAdjustment::createdAt, Comparator.nullsFirst(Comparator.naturalOrder())));
        List<Copayment> used = copayments.stream()
                .filter(copayment -> sales.stream().flatMap(sale -> sale.activeLines().stream()).anyMatch(copayment::usedBy))
                .filter(copayment -> usedAuthorizations.add(copayment.authorizationUuid()))
                .toList();
        BigDecimal patient;
        ShareSource source;
        if (adjustment.isPresent()) {
            patient = adjustment.get().amount().min(total);
            source = ShareSource.ADJUSTED;
        } else if (!covered) {
            patient = total;
            source = ShareSource.PRIVATE;
        } else {
            patient = used.stream().map(Copayment::amount).reduce(Money.ZERO, BigDecimal::add).min(total);
            source = ShareSource.COPAYMENT;
        }
        return new Unit(kind, saleUuid, sales, ready, notReady, Money.of(lines), packages, duplicates, total,
                Money.of(patient), source, used, adjustment.orElse(null), Money.of(total.subtract(patient)));
    }
}

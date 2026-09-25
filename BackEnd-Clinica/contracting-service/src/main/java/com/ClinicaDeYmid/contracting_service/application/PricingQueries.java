package com.ClinicaDeYmid.contracting_service.application;

import com.ClinicaDeYmid.contracting_service.domain.Capitation;
import com.ClinicaDeYmid.contracting_service.domain.Contract;
import com.ClinicaDeYmid.contracting_service.domain.ContractModality;
import com.ClinicaDeYmid.contracting_service.domain.ContractPackage;
import com.ClinicaDeYmid.contracting_service.domain.ContractTariffException;
import com.ClinicaDeYmid.contracting_service.domain.Contracts;
import com.ClinicaDeYmid.contracting_service.domain.ContractingException;
import com.ClinicaDeYmid.contracting_service.domain.PriceOrigin;
import com.ClinicaDeYmid.contracting_service.domain.PricedService;
import com.ClinicaDeYmid.contracting_service.domain.SurgicalComponent;
import com.ClinicaDeYmid.contracting_service.domain.SurgicalLiquidation;
import com.ClinicaDeYmid.contracting_service.domain.SurgicalRuleSet;
import com.ClinicaDeYmid.contracting_service.domain.TariffItem;
import com.ClinicaDeYmid.contracting_service.domain.TariffManualVersion;
import com.ClinicaDeYmid.contracting_service.domain.TariffManuals;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PricingQueries {

    private final Contracts contracts;
    private final TariffManuals manuals;
    private final Capitation capitation;

    public PricingQueries(Contracts contracts, TariffManuals manuals, Capitation capitation) {
        this.contracts = contracts;
        this.manuals = manuals;
        this.capitation = capitation;
    }

    public Quote quote(UUID contractUuid, LocalDate date, List<Requested> requested) {
        Contract contract = contracts.findByUuid(contractUuid).orElseThrow(ContractingException.ContractNotFound::new);
        contract.requireInForceOn(date);
        if (requested.isEmpty()) {
            throw new ContractingException.InvalidData("services", "debe traer al menos un servicio");
        }

        List<ContractPackage> packages = contracts.packagesInForce(contractUuid, date);
        List<PricedService> priced = new ArrayList<>();
        Map<UUID, ContractPackage> applied = new LinkedHashMap<>();

        for (Requested service : requested) {
            priced.add(price(contract, date, service, packages, applied).requiringAuthorization(
                    contracts.requirementFor(contractUuid, service.cupsCode(), date).isPresent()));
        }

        List<AppliedPackage> appliedPackages = applied.values().stream()
                .map(agreed -> new AppliedPackage(agreed.uuid(), agreed.code(), agreed.name(), agreed.price()))
                .toList();
        BigDecimal total = priced.stream()
                .map(PricedService::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(appliedPackages.stream().map(AppliedPackage::price).reduce(BigDecimal.ZERO, BigDecimal::add))
                .setScale(2, RoundingMode.HALF_UP);

        return new Quote(contract, date, tariffVersion(contract), priced, appliedPackages, total);
    }

    private PricedService price(Contract contract, LocalDate date, Requested service, List<ContractPackage> packages,
                                Map<UUID, ContractPackage> applied) {
        String code = service.cupsCode();
        int quantity = service.quantity();

        Optional<ContractPackage> covering = packages.stream().filter(agreed -> agreed.covers(code)).findFirst();
        if (covering.isPresent()) {
            ContractPackage agreed = covering.get();
            applied.putIfAbsent(agreed.uuid(), agreed);
            return PricedService.covered(code, quantity, PriceOrigin.PACKAGE, agreed.name(), agreed.uuid(), agreed.code());
        }

        if (contract.modality() == ContractModality.CAPITATION || contract.modality() == ContractModality.GLOBAL_BUDGET) {
            PriceOrigin origin = contract.modality() == ContractModality.CAPITATION
                    ? PriceOrigin.CAPITATION
                    : PriceOrigin.GLOBAL_BUDGET;
            UUID agreement = capitation.agreementInForce(contract.uuid(), date)
                    .map(found -> found.uuid())
                    .orElse(null);
            return PricedService.covered(code, quantity, origin, origin.label(), agreement, null);
        }

        Optional<ContractTariffException> exception = contracts.exceptionFor(contract.uuid(), code, date);
        if (exception.isPresent()) {
            ContractTariffException agreed = exception.get();
            return PricedService.priced(code, quantity, agreed.agreedPrice(), PriceOrigin.CONTRACT_EXCEPTION,
                    agreed.reason(), agreed.uuid(), null);
        }

        TariffManualVersion version = contract.tariffVersion();
        if (version == null) {
            return PricedService.covered(code, quantity, PriceOrigin.UNPRICED, PriceOrigin.UNPRICED.label(), null, null);
        }
        return manuals.findItem(version.uuid(), code)
                .map(item -> item.surgical()
                        ? PricedService.surgicalProcedure(code, quantity, item.description())
                        : fromManual(contract, version, item, quantity))
                .orElseGet(() -> PricedService.covered(code, quantity, PriceOrigin.UNPRICED,
                        PriceOrigin.UNPRICED.label(), null, null));
    }

    private PricedService fromManual(Contract contract, TariffManualVersion version, TariffItem item, int quantity) {
        BigDecimal unitPrice = item.inPesos().multiply(contract.tariffFactor()).setScale(2, RoundingMode.HALF_UP);
        return PricedService.priced(item.cupsCode(), quantity, unitPrice, PriceOrigin.TARIFF_MANUAL,
                item.description(), version.uuid(), version.manual().code());
    }

    private TariffManualVersion tariffVersion(Contract contract) {
        return contract.tariffVersion();
    }

    public SurgicalQuote surgicalQuote(UUID contractUuid, LocalDate date, List<RequestedProcedure> requested) {
        Contract contract = contracts.findByUuid(contractUuid).orElseThrow(ContractingException.ContractNotFound::new);
        contract.requireInForceOn(date);
        if (requested.isEmpty()) {
            throw new ContractingException.InvalidData("procedures", "debe traer al menos un procedimiento");
        }
        List<ContractPackage> packages = contracts.packagesInForce(contractUuid, date);
        Map<UUID, ContractPackage> applied = new LinkedHashMap<>();
        QuotedProcedure[] quoted = new QuotedProcedure[requested.size()];
        List<SurgicalLiquidation.Procedure> toLiquidate = new ArrayList<>();
        TariffManualVersion version = contract.tariffVersion();

        for (int index = 0; index < requested.size(); index++) {
            RequestedProcedure procedure = requested.get(index);
            boolean authorization = contracts.requirementFor(contractUuid, procedure.cupsCode(), date).isPresent();
            Optional<TariffItem> item = version == null || contract.modality() == ContractModality.CAPITATION
                    || contract.modality() == ContractModality.GLOBAL_BUDGET
                    ? Optional.empty() : manuals.findItem(version.uuid(), procedure.cupsCode());
            if (item.isPresent() && item.get().surgical() && !coveredBeforeTheManual(contract, date, procedure, packages)) {
                toLiquidate.add(new SurgicalLiquidation.Procedure(index, item.get(), procedure.route()));
                continue;
            }
            PricedService priced = price(contract, date, new Requested(procedure.cupsCode(), 1), packages, applied);
            quoted[index] = QuotedProcedure.single(procedure, priced, authorization);
        }

        SurgicalRuleSet rules = null;
        if (!toLiquidate.isEmpty()) {
            rules = manuals.surgicalRulesOf(version.uuid()).orElseThrow(ContractingException.SurgicalRulesMissing::new);
            for (SurgicalLiquidation.Liquidated liquidated : SurgicalLiquidation.liquidate(rules, contract.tariffFactor(),
                    toLiquidate)) {
                RequestedProcedure procedure = requested.get(liquidated.requestIndex());
                quoted[liquidated.requestIndex()] = QuotedProcedure.liquidated(procedure, liquidated, version,
                        contracts.requirementFor(contractUuid, procedure.cupsCode(), date).isPresent());
            }
        }

        List<AppliedPackage> appliedPackages = applied.values().stream()
                .map(agreed -> new AppliedPackage(agreed.uuid(), agreed.code(), agreed.name(), agreed.price()))
                .toList();
        List<QuotedProcedure> procedures = List.of(quoted);
        Map<SurgicalComponent, BigDecimal> byComponent = new EnumMap<>(SurgicalComponent.class);
        procedures.forEach(procedure -> procedure.components().forEach(charge ->
                byComponent.merge(charge.component(), charge.amount(), BigDecimal::add)));
        BigDecimal total = procedures.stream().map(QuotedProcedure::total).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(appliedPackages.stream().map(AppliedPackage::price).reduce(BigDecimal.ZERO, BigDecimal::add))
                .setScale(2, RoundingMode.HALF_UP);
        return new SurgicalQuote(contract, date, version, rules, procedures, appliedPackages, byComponent, total);
    }

    private boolean coveredBeforeTheManual(Contract contract, LocalDate date, RequestedProcedure procedure,
                                           List<ContractPackage> packages) {
        return packages.stream().anyMatch(agreed -> agreed.covers(procedure.cupsCode()))
                || contracts.exceptionFor(contract.uuid(), procedure.cupsCode(), date).isPresent();
    }

    public record RequestedProcedure(String cupsCode, String route) {

        public RequestedProcedure {
            if (cupsCode == null || cupsCode.isBlank()) {
                throw new ContractingException.InvalidData("cupsCode", "es obligatorio");
            }
            route = route == null || route.isBlank() ? "UNICA" : route.strip().toUpperCase(java.util.Locale.ROOT);
            if (route.length() > 30) {
                throw new ContractingException.InvalidData("route", "no puede superar 30 caracteres");
            }
        }
    }

    public record QuotedProcedure(String cupsCode, String route, String description, PriceOrigin origin,
                                  BigDecimal basis, Integer order, boolean principal, boolean sameRoute,
                                  List<SurgicalLiquidation.ComponentCharge> components, BigDecimal total,
                                  UUID referenceUuid, String referenceCode, boolean authorizationRequired) {

        static QuotedProcedure single(RequestedProcedure procedure, PricedService priced, boolean authorization) {
            return new QuotedProcedure(procedure.cupsCode(), procedure.route(), priced.description(), priced.origin(),
                    null, null, false, false, List.of(), priced.lineTotal(), priced.referenceUuid(),
                    priced.referenceCode(), authorization);
        }

        static QuotedProcedure liquidated(RequestedProcedure procedure, SurgicalLiquidation.Liquidated liquidated,
                                          TariffManualVersion version, boolean authorization) {
            return new QuotedProcedure(procedure.cupsCode(), procedure.route(), liquidated.item().description(),
                    PriceOrigin.SURGICAL_LIQUIDATION, liquidated.item().surgicalBasis(), liquidated.order(),
                    liquidated.principal(), liquidated.sameRoute(), liquidated.components(), liquidated.total(),
                    version.uuid(), version.manual().code(), authorization);
        }
    }

    public record SurgicalQuote(Contract contract, LocalDate date, TariffManualVersion tariffVersion,
                                SurgicalRuleSet rules, List<QuotedProcedure> procedures, List<AppliedPackage> packages,
                                Map<SurgicalComponent, BigDecimal> componentTotals, BigDecimal total) {
    }

    public record Requested(String cupsCode, int quantity) {

        public Requested {
            if (cupsCode == null || cupsCode.isBlank()) {
                throw new ContractingException.InvalidData("cupsCode", "es obligatorio");
            }
            if (quantity < 1) {
                throw new ContractingException.InvalidData("quantity", "debe ser al menos 1");
            }
        }
    }

    public record AppliedPackage(UUID uuid, String code, String name, BigDecimal price) {
    }

    public record Quote(Contract contract, LocalDate date, TariffManualVersion tariffVersion, List<PricedService> services,
                        List<AppliedPackage> packages, BigDecimal total) {
    }
}

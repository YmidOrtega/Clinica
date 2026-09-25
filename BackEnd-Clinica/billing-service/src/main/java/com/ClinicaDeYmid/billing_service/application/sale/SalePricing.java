package com.ClinicaDeYmid.billing_service.application.sale;

import com.ClinicaDeYmid.billing_service.application.context.EpisodeDetails;
import com.ClinicaDeYmid.billing_service.application.context.EpisodeDirectory;
import com.ClinicaDeYmid.billing_service.application.context.EpisodeLookup;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.LinePrice;
import com.ClinicaDeYmid.billing_service.domain.PackageCharge;
import com.ClinicaDeYmid.billing_service.domain.PricingTerms;
import com.ClinicaDeYmid.billing_service.domain.Sale;
import com.ClinicaDeYmid.billing_service.domain.SaleLine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class SalePricing {

    static final String COVERED = "COVERED";
    static final String UNKNOWN = "UNKNOWN";

    private final EpisodeDirectory episodes;
    private final PriceQuotes quotes;
    private final Optional<UUID> privateContract;

    public SalePricing(EpisodeDirectory episodes, PriceQuotes quotes,
                       @Value("${clinica.billing.pricing.private-contract-uuid:}") String privateContract) {
        this.episodes = episodes;
        this.quotes = quotes;
        this.privateContract = privateContract == null || privateContract.isBlank()
                ? Optional.empty() : Optional.of(UUID.fromString(privateContract.strip()));
    }

    public PricingTerms termsFor(Sale sale) {
        return termsFor(sale.account().admissionUuid(), sale.activeLines());
    }

    public PricingTerms termsFor(UUID admissionUuid, List<SaleLine> lines) {
        Optional<UUID> contract = contractOf(admissionUuid);
        if (contract.isEmpty() || lines.isEmpty()) {
            return PricingTerms.withoutContract();
        }
        Map<LocalDate, List<SaleLine>> byDate = new TreeMap<>();
        lines.forEach(line -> byDate.computeIfAbsent(line.serviceDate(), day -> new java.util.ArrayList<>()).add(line));
        Map<UUID, LinePrice> prices = new HashMap<>();
        Map<UUID, PackageCharge> packages = new LinkedHashMap<>();
        String contractNumber = null;
        UUID payer = null;
        for (Map.Entry<LocalDate, List<SaleLine>> day : byDate.entrySet()) {
            List<SaleLine> served = day.getValue();
            QuoteLookup.Quoted quoted = switch (quotes.quote(contract.get(), day.getKey(), served.stream()
                    .map(line -> new PriceQuotes.Requested(line.service().cupsCode(), line.quantity())).toList())) {
                case QuoteLookup.Quoted found -> found;
                case QuoteLookup.Refused refused -> throw new BillingException.ContractCannotPrice(refused.detail());
                case QuoteLookup.Unavailable ignored -> throw new BillingException.ContractingUnavailable();
            };
            if (quoted.services().size() != served.size()) {
                throw new BillingException.ContractCannotPrice("la cotización no trajo un precio por cada servicio");
            }
            for (int index = 0; index < served.size(); index++) {
                QuoteLookup.Service service = quoted.services().get(index);
                prices.put(served.get(index).uuid(), new LinePrice(service.origin(), service.unitPrice(),
                        service.lineTotal(), service.referenceUuid(), service.referenceCode()));
            }
            quoted.packages().forEach(applied -> packages.putIfAbsent(applied.uuid(),
                    new PackageCharge(applied.uuid(), applied.code(), applied.name(), applied.price())));
            contractNumber = quoted.contractNumber();
            payer = quoted.payerUuid();
        }
        return new PricingTerms(contract.get(), contractNumber, payer, prices, List.copyOf(packages.values()));
    }

    private Optional<UUID> contractOf(UUID admissionUuid) {
        EpisodeDetails episode = switch (episodes.episode(admissionUuid)) {
            case EpisodeLookup.Found found -> found.episode();
            case EpisodeLookup.NotFound ignored -> throw new BillingException.EpisodeUnknownToAdmissions();
            case EpisodeLookup.Unavailable ignored -> throw new BillingException.AdmissionsUnavailable();
        };
        EpisodeDetails.Coverage coverage = episode.coverage();
        if (coverage != null && UNKNOWN.equals(coverage.status())) {
            throw new BillingException.CoveragePending();
        }
        if (coverage != null && COVERED.equals(coverage.status()) && coverage.contractUuid() != null) {
            return Optional.of(coverage.contractUuid());
        }
        return privateContract;
    }
}

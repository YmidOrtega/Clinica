package com.ClinicaDeYmid.billing_service.application.sale;

import com.ClinicaDeYmid.billing_service.application.context.EpisodeDetails;
import com.ClinicaDeYmid.billing_service.application.context.EpisodeDirectory;
import com.ClinicaDeYmid.billing_service.application.context.EpisodeLookup;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.ChargedService;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccount;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccounts;
import com.ClinicaDeYmid.billing_service.domain.FeeAgreementTerms;
import com.ClinicaDeYmid.billing_service.domain.LineOrigin;
import com.ClinicaDeYmid.billing_service.domain.LinePrice;
import com.ClinicaDeYmid.billing_service.domain.PractitionerFee;
import com.ClinicaDeYmid.billing_service.domain.PractitionerFees;
import com.ClinicaDeYmid.billing_service.domain.PricedSale;
import com.ClinicaDeYmid.billing_service.domain.PricingTerms;
import com.ClinicaDeYmid.billing_service.domain.Sale;
import com.ClinicaDeYmid.billing_service.domain.SaleLine;
import com.ClinicaDeYmid.billing_service.domain.SaleType;
import com.ClinicaDeYmid.billing_service.domain.Sales;
import com.ClinicaDeYmid.billing_service.domain.SurgicalRole;
import com.ClinicaDeYmid.billing_service.domain.TeamMember;
import com.ClinicaDeYmid.commons.web.EntityTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class SaleCommands {

    private static final Logger log = LoggerFactory.getLogger(SaleCommands.class);

    private final EpisodeAccounts accounts;
    private final Sales sales;
    private final EpisodeDirectory episodes;
    private final PortfolioCatalogue portfolio;
    private final SalePricing pricing;
    private final PractitionerDirectory practitioners;
    private final FeeAgreements feeAgreements;
    private final PractitionerFees fees;
    private final TransactionOperations transactions;
    private final Clock clock;

    public SaleCommands(EpisodeAccounts accounts, Sales sales, EpisodeDirectory episodes, PortfolioCatalogue portfolio,
                        SalePricing pricing, PractitionerDirectory practitioners, FeeAgreements feeAgreements,
                        PractitionerFees fees, TransactionOperations transactions, Clock clock) {
        this.accounts = accounts;
        this.sales = sales;
        this.episodes = episodes;
        this.portfolio = portfolio;
        this.pricing = pricing;
        this.practitioners = practitioners;
        this.feeAgreements = feeAgreements;
        this.fees = fees;
        this.transactions = transactions;
        this.clock = clock;
    }

    public OpenedSale open(String admissionNumber, SaleType type, boolean preloadAuthorized) {
        EpisodeAccount found = accounts.findByAdmissionNumber(admissionNumber)
                .orElseThrow(BillingException.AccountNotFound::new);
        List<String> notes = new ArrayList<>();
        List<PlannedLine> planned = preloadAuthorized && type instanceof SaleType.NonSurgical
                ? authorizedLines(found.admissionUuid(), notes) : List.of();
        LocalDate today = LocalDate.now(clock);
        Sale opened = transactions.execute(status -> {
            EpisodeAccount account = accounts.lockByAdmission(found.admissionUuid())
                    .orElseThrow(BillingException.AccountNotFound::new);
            Sale sale = Sale.open(account, sales.countByAccount(account.uuid()) + 1, type);
            planned.forEach(line -> sale.charge(line.service(), 1, today, line.origin(), clock));
            return sales.save(sale);
        });
        log.info("Sale {} opened for admission {} with {} authorized lines", opened.number(), admissionNumber,
                planned.size());
        return new OpenedSale(opened, notes);
    }

    public Sale charge(UUID saleUuid, long expectedVersion, LineRequest request) {
        ChargedService service = resolve(request);
        LocalDate serviceDate = request.serviceDate() == null ? LocalDate.now(clock) : request.serviceDate();
        return modify(saleUuid, expectedVersion,
                sale -> sale.charge(service, request.quantity(), serviceDate, new LineOrigin.Manual(), clock));
    }

    public Sale chargeProcedure(UUID saleUuid, long expectedVersion, UUID portfolioItemUuid, String cupsCode,
                                String route) {
        ChargedService service = resolve(new LineRequest(portfolioItemUuid, cupsCode, 1, null));
        return modify(saleUuid, expectedVersion,
                sale -> sale.chargeProcedure(service, route, new LineOrigin.Manual(), clock));
    }

    public Sale assignTeam(UUID saleUuid, long expectedVersion, List<TeamAssignment> assignments) {
        List<TeamMember> members = assignments.stream().map(this::member).toList();
        Sale assigned = modify(saleUuid, expectedVersion, sale -> sale.assignTeam(members, clock));
        log.info("Surgical team of sale {} set to {} members", assigned.number(), members.size());
        return assigned;
    }

    private TeamMember member(TeamAssignment assignment) {
        return switch (practitioners.practitioner(assignment.practitionerUuid())) {
            case PractitionerLookup.Found found when found.attends() -> new TeamMember(assignment.role(),
                    assignment.practitionerUuid(), found.fullName(), found.registrationNumber());
            case PractitionerLookup.Found ignored -> throw new BillingException.PractitionerNotAvailable();
            case PractitionerLookup.NotFound ignored -> throw new BillingException.PractitionerNotAvailable();
            case PractitionerLookup.Unavailable ignored -> throw new BillingException.PractitionersUnavailable();
        };
    }

    public record TeamAssignment(SurgicalRole role, UUID practitionerUuid) {
    }

    public Sale removeLine(UUID saleUuid, long expectedVersion, UUID lineUuid, String reason) {
        return modify(saleUuid, expectedVersion, sale -> sale.removeLine(lineUuid, reason, clock));
    }

    public Sale confirm(UUID saleUuid, long expectedVersion) {
        Sale current = current(saleUuid, expectedVersion);
        current.requireConfirmable();
        PricingTerms terms = pricing.termsFor(current);
        Map<UUID, Optional<FeeAgreementTerms>> agreements = agreementsOf(current);
        Sale confirmed = transactions.execute(status -> {
            Sale sale = current(saleUuid, expectedVersion);
            sale.confirm(terms, clock);
            Sale saved = sales.save(sale);
            List<PractitionerFee> owed = fees.saveAll(PractitionerFee.owedFor(saved, agreements));
            if (!owed.isEmpty()) {
                log.info("Sale {} owes {} practitioner fees", saved.number(), owed.size());
            }
            return saved;
        });
        log.info("Sale {} confirmed with {} lines for {}", confirmed.number(), confirmed.activeLines().size(),
                confirmed.settlement().map(Sale.Settlement::total).orElse(null));
        return confirmed;
    }

    public Sale priceManually(UUID saleUuid, long expectedVersion, UUID lineUuid, BigDecimal unitPrice, String reason) {
        Sale current = current(saleUuid, expectedVersion);
        SaleLine line = current.line(lineUuid);
        LinePrice quoted = pricing.termsFor(current.account().admissionUuid(), List.of(line)).quoted().get(lineUuid);
        if (quoted != null && !quoted.pending()) {
            throw new BillingException.LinePricedByContract();
        }
        Sale priced = modify(saleUuid, expectedVersion, sale -> sale.priceManually(lineUuid, unitPrice, reason, clock));
        log.info("Line {} of sale {} priced manually at {}", lineUuid, priced.number(), unitPrice);
        return priced;
    }

    public PricedSale preview(UUID saleUuid) {
        Sale sale = sales.findByUuid(saleUuid).orElseThrow(BillingException.SaleNotFound::new);
        return sale.price(pricing.termsFor(sale), clock.getZone());
    }

    private Map<UUID, Optional<FeeAgreementTerms>> agreementsOf(Sale sale) {
        if (!(sale.type() instanceof SaleType.Surgical surgical)) {
            return Map.of();
        }
        Map<UUID, Optional<FeeAgreementTerms>> agreements = new HashMap<>();
        sale.surgicalTeam().forEach(member -> agreements.put(member.practitionerUuid(),
                feeAgreements.inForce(member.practitionerUuid(), surgical.performedOn())));
        return agreements;
    }

    private Sale current(UUID saleUuid, long expectedVersion) {
        Sale sale = sales.findByUuid(saleUuid).orElseThrow(BillingException.SaleNotFound::new);
        if (sale.version() != expectedVersion) {
            throw new EntityTags.StaleVersion();
        }
        return sale;
    }

    public Sale cancel(UUID saleUuid, long expectedVersion, String reason) {
        Sale cancelled = transactions.execute(status -> {
            Sale sale = current(saleUuid, expectedVersion);
            sale.cancel(reason, clock);
            Sale saved = sales.save(sale);
            List<PractitionerFee> owed = fees.findBySale(saleUuid);
            owed.forEach(fee -> fee.voidBecause("La venta " + saved.number() + " fue anulada: " + reason));
            fees.saveAll(owed);
            return saved;
        });
        log.info("Sale {} cancelled", cancelled.number());
        return cancelled;
    }

    private Sale modify(UUID saleUuid, long expectedVersion, Consumer<Sale> change) {
        return transactions.execute(status -> {
            Sale sale = sales.findByUuid(saleUuid).orElseThrow(BillingException.SaleNotFound::new);
            if (sale.version() != expectedVersion) {
                throw new EntityTags.StaleVersion();
            }
            change.accept(sale);
            return sales.save(sale);
        });
    }

    private ChargedService resolve(LineRequest request) {
        PortfolioLookup lookup = request.portfolioItemUuid() != null
                ? portfolio.byUuid(request.portfolioItemUuid())
                : portfolio.byCups(request.cupsCode().strip());
        String requested = request.cupsCode() != null ? request.cupsCode().strip() : String.valueOf(request.portfolioItemUuid());
        return switch (lookup) {
            case PortfolioLookup.Found found when found.service().offered() -> found.service().charged();
            case PortfolioLookup.Found found -> throw new BillingException.ServiceNotOffered(found.service().cupsCode());
            case PortfolioLookup.Ambiguous ignored -> throw new BillingException.AmbiguousService(requested);
            case PortfolioLookup.NotFound ignored -> throw new BillingException.ServiceNotOffered(requested);
            case PortfolioLookup.Unavailable ignored -> throw new BillingException.ContractingUnavailable();
        };
    }

    private List<PlannedLine> authorizedLines(UUID admissionUuid, List<String> notes) {
        EpisodeDetails episode = switch (episodes.episode(admissionUuid)) {
            case EpisodeLookup.Found found -> found.episode();
            case EpisodeLookup.NotFound ignored -> throw new BillingException.EpisodeUnknownToAdmissions();
            case EpisodeLookup.Unavailable ignored -> throw new BillingException.AdmissionsUnavailable();
        };
        List<PlannedLine> planned = new ArrayList<>();
        for (EpisodeDetails.Authorization authorization : episode.authorizations()) {
            LineOrigin origin = new LineOrigin.Authorized(authorization.uuid(), authorization.number());
            for (UUID item : authorization.authorizedItems()) {
                switch (portfolio.byUuid(item)) {
                    case PortfolioLookup.Found found when found.service().offered() ->
                            planned.add(new PlannedLine(found.service().charged(), origin));
                    case PortfolioLookup.Found found -> notes.add("La autorización " + authorization.number()
                            + " incluye " + found.service().cupsCode() + ", que ya no está activo en el portafolio");
                    case PortfolioLookup.NotFound ignored -> notes.add("La autorización " + authorization.number()
                            + " incluye un servicio que no existe en el portafolio (" + item + ")");
                    case PortfolioLookup.Ambiguous ignored -> notes.add("La autorización " + authorization.number()
                            + " incluye un servicio ambiguo (" + item + ")");
                    case PortfolioLookup.Unavailable ignored -> throw new BillingException.ContractingUnavailable();
                }
            }
        }
        return planned;
    }

    private record PlannedLine(ChargedService service, LineOrigin origin) {
    }
}

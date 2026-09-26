package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.application.context.EpisodeDetails;
import com.ClinicaDeYmid.billing_service.application.context.EpisodeDirectory;
import com.ClinicaDeYmid.billing_service.application.context.EpisodeLookup;
import com.ClinicaDeYmid.billing_service.domain.AccountSummary;
import com.ClinicaDeYmid.billing_service.domain.AdmissionKind;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.Copayment;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccount;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccounts;
import com.ClinicaDeYmid.billing_service.domain.PatientShareAdjustment;
import com.ClinicaDeYmid.billing_service.domain.PatientShareAdjustments;
import com.ClinicaDeYmid.billing_service.domain.Sale;
import com.ClinicaDeYmid.billing_service.domain.SaleStatus;
import com.ClinicaDeYmid.billing_service.domain.Sales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AccountSummaries {

    static final String NOT_COVERED = "NOT_COVERED";

    private static final Logger log = LoggerFactory.getLogger(AccountSummaries.class);

    private final EpisodeAccounts accounts;
    private final Sales sales;
    private final PatientShareAdjustments adjustments;
    private final EpisodeDirectory episodes;
    private final TransactionOperations transactions;

    public AccountSummaries(EpisodeAccounts accounts, Sales sales, PatientShareAdjustments adjustments,
                            EpisodeDirectory episodes, TransactionOperations transactions) {
        this.accounts = accounts;
        this.sales = sales;
        this.adjustments = adjustments;
        this.episodes = episodes;
        this.transactions = transactions;
    }

    public AccountSummary summarize(String admissionNumber) {
        EpisodeAccount found = accountOf(admissionNumber);
        EpisodeDetails episode = episodeOf(found);
        boolean covered = episode.coverage() != null && !NOT_COVERED.equals(episode.coverage().status());
        List<Copayment> copayments = episode.authorizations().stream()
                .map(authorization -> new Copayment(authorization.uuid(), authorization.number(),
                        authorization.copayment(), authorization.validFrom(), authorization.validTo(),
                        authorization.authorizedItems(), authorization.coversEverything()))
                .toList();
        return transactions.execute(status -> {
            EpisodeAccount account = accountOf(admissionNumber);
            return AccountSummary.of(account, sales.findByAccount(account.uuid()), covered, copayments,
                    adjustments.findByAccount(account.uuid()));
        });
    }

    public PatientShareAdjustment adjust(String admissionNumber, UUID saleUuid, BigDecimal amount, String reason) {
        return transactions.execute(status -> {
            EpisodeAccount account = accountOf(admissionNumber);
            if (account.kind() == AdmissionKind.OUTPATIENT) {
                if (saleUuid == null) {
                    throw new BillingException.NotABillableUnit(
                            "Un episodio ambulatorio se factura venta por venta; indica saleUuid");
                }
                Sale sale = sales.findByUuid(saleUuid).orElseThrow(BillingException.SaleNotFound::new);
                if (!sale.account().uuid().equals(account.uuid()) || !(sale.status() instanceof SaleStatus.Confirmed)) {
                    throw new BillingException.NotABillableUnit("La venta debe ser una venta confirmada de este episodio");
                }
            } else if (saleUuid != null) {
                throw new BillingException.NotABillableUnit(
                        "Urgencias y hospitalización se facturan por cuenta completa; no indiques saleUuid");
            }
            PatientShareAdjustment saved = adjustments.save(PatientShareAdjustment.of(account, saleUuid, amount, reason));
            log.info("Patient share of {} adjusted to {}{}", admissionNumber, amount,
                    saleUuid == null ? "" : " for sale " + saleUuid);
            return saved;
        });
    }

    private EpisodeAccount accountOf(String admissionNumber) {
        return accounts.findByAdmissionNumber(admissionNumber).orElseThrow(BillingException.AccountNotFound::new);
    }

    private EpisodeDetails episodeOf(EpisodeAccount account) {
        return switch (episodes.episode(account.admissionUuid())) {
            case EpisodeLookup.Found found -> found.episode();
            case EpisodeLookup.NotFound ignored -> throw new BillingException.EpisodeUnknownToAdmissions();
            case EpisodeLookup.Unavailable ignored -> throw new BillingException.AdmissionsUnavailable();
        };
    }
}

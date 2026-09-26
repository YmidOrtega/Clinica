package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.application.context.EpisodeDetails;
import com.ClinicaDeYmid.billing_service.application.context.PatientDetails;
import com.ClinicaDeYmid.billing_service.application.context.PatientDirectory;
import com.ClinicaDeYmid.billing_service.application.context.PatientLookup;
import com.ClinicaDeYmid.billing_service.application.context.PayerDirectory;
import com.ClinicaDeYmid.billing_service.application.context.PayerLookup;
import com.ClinicaDeYmid.billing_service.application.dian.DianSoftware;
import com.ClinicaDeYmid.billing_service.domain.AccountSummary;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.Buyer;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocument;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccount;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccounts;
import com.ClinicaDeYmid.billing_service.domain.HealthUser;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.Invoices;
import com.ClinicaDeYmid.commons.web.EntityTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class InvoiceCommands {

    static final String UNKNOWN = "UNKNOWN";
    static final String COVERED = "COVERED";

    private static final Logger log = LoggerFactory.getLogger(InvoiceCommands.class);

    private final AccountSummaries summaries;
    private final EpisodeAccounts accounts;
    private final Invoices invoices;
    private final InvoiceIssuance issuance;
    private final PayerDirectory payers;
    private final PatientDirectory patients;
    private final DianSoftware software;
    private final TransactionOperations transactions;
    private final Clock clock;

    public InvoiceCommands(AccountSummaries summaries, EpisodeAccounts accounts, Invoices invoices,
                           InvoiceIssuance issuance, PayerDirectory payers, PatientDirectory patients,
                           DianSoftware software, TransactionOperations transactions, Clock clock) {
        this.summaries = summaries;
        this.accounts = accounts;
        this.invoices = invoices;
        this.issuance = issuance;
        this.payers = payers;
        this.patients = patients;
        this.software = software;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Invoice draft(String admissionNumber, UUID saleUuid) {
        AccountSummaries.Context context = summaries.context(admissionNumber);
        EpisodeDetails.Coverage coverage = requireResolved(context.episode());
        AccountSummary.Unit unit = unitOf(context.summary(), saleUuid);
        PatientDetails patient = switch (patients.patient(context.summary().account().patientUuid())) {
            case PatientLookup.Found found -> found.patient();
            case PatientLookup.NotFound ignored ->
                    throw new BillingException.BuyerNotIdentified("El paciente del episodio no está en el directorio");
            case PatientLookup.Unavailable ignored -> throw new BillingException.PatientsUnavailable();
        };
        Buyer buyer = covered(coverage) ? payerOf(coverage) : patientAsBuyer(patient);
        HealthUser user = userOf(patient);
        Invoice drafted;
        try {
            drafted = transactions.execute(status -> {
                EpisodeAccount account = accounts.findByAdmissionNumber(admissionNumber)
                        .orElseThrow(BillingException.AccountNotFound::new);
                List<Invoice> shared = buyer.kind() == Buyer.Kind.PAYER
                        ? SharedPaymentAllocation.forUnit(context.summary(), unit, invoices.sharedPaymentsOf(account.uuid()))
                        : List.of();
                return invoices.save(Invoice.draft(unit, account, buyer, user, shared));
            });
        } catch (DataIntegrityViolationException taken) {
            throw new BillingException.UnitAlreadyInvoiced();
        }
        log.info("Invoice draft {} prepared for {} ({}) to {}", drafted.uuid(), admissionNumber, unit.kind(),
                buyer.kind());
        return drafted;
    }

    public ElectronicDocument issue(UUID invoiceUuid, long expectedVersion) {
        Invoice current = current(invoiceUuid, expectedVersion);
        AccountSummaries.Context context = summaries.context(current.account().admissionNumber());
        requireResolved(context.episode());
        AccountSummary.Unit unit = unitOf(context.summary(), current.saleUuid());
        if (!unit.ready() || !current.stillMatches(unit)) {
            throw new BillingException.InvoiceOutdated();
        }
        software.requireConfigured();
        ElectronicDocument issued = transactions.execute(status -> {
            Invoice invoice = current(invoiceUuid, expectedVersion);
            if (invoice.buyer().kind() == Buyer.Kind.PAYER) {
                invoice.deduct(SharedPaymentAllocation.forUnit(context.summary(), unit,
                        invoices.sharedPaymentsOf(invoice.account().uuid())));
            }
            return issuance.issue(invoice);
        });
        log.info("Invoice {} issued for {} with total {}", issued.number(), current.account().admissionNumber(),
                current.payableTotal());
        return issued;
    }

    public Invoice discard(UUID invoiceUuid, long expectedVersion, String reason) {
        return transactions.execute(status -> {
            Invoice invoice = current(invoiceUuid, expectedVersion);
            invoice.discard(reason, clock);
            return invoices.save(invoice);
        });
    }

    private Invoice current(UUID invoiceUuid, long expectedVersion) {
        Invoice invoice = invoices.findByUuid(invoiceUuid).orElseThrow(BillingException.InvoiceNotFound::new);
        if (invoice.version() != expectedVersion) {
            throw new EntityTags.StaleVersion();
        }
        return invoice;
    }

    private static EpisodeDetails.Coverage requireResolved(EpisodeDetails episode) {
        EpisodeDetails.Coverage coverage = episode.coverage();
        if (coverage != null && UNKNOWN.equals(coverage.status())) {
            throw new BillingException.CoveragePending();
        }
        return coverage;
    }

    private static boolean covered(EpisodeDetails.Coverage coverage) {
        return coverage != null && COVERED.equals(coverage.status()) && coverage.payerUuid() != null;
    }

    private static AccountSummary.Unit unitOf(AccountSummary summary, UUID saleUuid) {
        return summary.units().stream()
                .filter(unit -> saleUuid == null ? unit.kind() == AccountSummary.UnitKind.ACCOUNT
                        : saleUuid.equals(unit.saleUuid()))
                .findFirst()
                .orElseThrow(() -> new BillingException.NotABillableUnit(saleUuid == null
                        ? "El episodio no tiene una cuenta para facturar completa; si es ambulatorio indica saleUuid"
                        : "Esa venta no es una unidad facturable de este episodio"));
    }

    private Buyer payerOf(EpisodeDetails.Coverage coverage) {
        return switch (payers.payer(coverage.payerUuid())) {
            case PayerLookup.Found found -> new Buyer(Buyer.Kind.PAYER, found.payer().uuid(), "NIT",
                    found.payer().nit(), found.payer().name());
            case PayerLookup.NotFound ignored ->
                    throw new BillingException.BuyerNotIdentified("El pagador de la cobertura no está en contratación");
            case PayerLookup.Unavailable ignored -> throw new BillingException.ContractingUnavailable();
        };
    }

    static Buyer patientAsBuyer(PatientDetails patient) {
        if (!(patient instanceof PatientDetails.Registered registered)) {
            throw new BillingException.BuyerNotIdentified(
                    "Un paciente particular sin identificar no puede ser el adquiriente de la factura");
        }
        return new Buyer(Buyer.Kind.PATIENT, registered.uuid(), registered.documentType(), registered.documentNumber(),
                registered.firstNames() + " " + registered.lastNames());
    }

    static HealthUser userOf(PatientDetails patient) {
        return switch (patient) {
            case PatientDetails.Registered registered -> new HealthUser(registered.uuid(), registered.documentType(),
                    registered.documentNumber(), registered.firstNames() + " " + registered.lastNames(),
                    registered.healthRegime());
            case PatientDetails.Unidentified unidentified -> new HealthUser(unidentified.uuid(), "NO_IDENTIFICADO",
                    unidentified.code(), "Paciente no identificado " + unidentified.code(), null);
        };
    }
}

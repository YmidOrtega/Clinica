package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.application.context.EpisodeDetails;
import com.ClinicaDeYmid.billing_service.application.context.PatientDetails;
import com.ClinicaDeYmid.billing_service.application.context.PatientDirectory;
import com.ClinicaDeYmid.billing_service.application.context.PatientLookup;
import com.ClinicaDeYmid.billing_service.application.dian.DianSoftware;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocument;
import com.ClinicaDeYmid.billing_service.domain.ElectronicDocuments;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccount;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccounts;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.Invoices;
import com.ClinicaDeYmid.billing_service.domain.Money;
import com.ClinicaDeYmid.billing_service.domain.SharedPaymentKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.util.Objects;

@Service
public class SharedPaymentCommands {

    private static final Logger log = LoggerFactory.getLogger(SharedPaymentCommands.class);

    public record Collected(String admissionNumber, String authorizationNumber, SharedPaymentKind kind,
                            BigDecimal amount, String collectionReference) {
    }

    public record Invoiced(ElectronicDocument document, boolean created) {
    }

    private final AccountSummaries summaries;
    private final EpisodeAccounts accounts;
    private final Invoices invoices;
    private final ElectronicDocuments documents;
    private final InvoiceIssuance issuance;
    private final PatientDirectory patients;
    private final DianSoftware software;
    private final TransactionOperations transactions;

    public SharedPaymentCommands(AccountSummaries summaries, EpisodeAccounts accounts, Invoices invoices,
                                 ElectronicDocuments documents, InvoiceIssuance issuance, PatientDirectory patients,
                                 DianSoftware software, TransactionOperations transactions) {
        this.summaries = summaries;
        this.accounts = accounts;
        this.invoices = invoices;
        this.documents = documents;
        this.issuance = issuance;
        this.patients = patients;
        this.software = software;
        this.transactions = transactions;
    }

    public Invoiced invoice(Collected collected) {
        var earlier = invoices.findByCollectionReference(collected.collectionReference());
        if (earlier.isPresent()) {
            return new Invoiced(sameCollection(earlier.get(), collected), false);
        }
        software.requireConfigured();
        AccountSummaries.Context context = summaries.context(collected.admissionNumber());
        EpisodeDetails.Coverage coverage = context.episode().coverage();
        if (coverage == null || !InvoiceCommands.COVERED.equals(coverage.status())) {
            throw new BillingException.SharedPaymentNotAccepted(
                    "Solo se factura un copago o una cuota de un episodio con cobertura confirmada");
        }
        String authorization = collected.authorizationNumber() == null || collected.authorizationNumber().isBlank()
                ? null : collected.authorizationNumber().strip();
        if (authorization != null && context.episode().authorizations().stream()
                .noneMatch(known -> authorization.equals(known.number()))) {
            throw new BillingException.SharedPaymentNotAccepted(
                    "La autorización " + authorization + " no pertenece a este episodio");
        }
        PatientDetails patient = switch (patients.patient(context.summary().account().patientUuid())) {
            case PatientLookup.Found found -> found.patient();
            case PatientLookup.NotFound ignored -> throw new BillingException.BuyerNotIdentified(
                    "El paciente del episodio no está en el directorio");
            case PatientLookup.Unavailable ignored -> throw new BillingException.PatientsUnavailable();
        };
        SharedPaymentKind kind = collected.kind() != null ? collected.kind()
                : proposedKind(patient, context.summary().account());
        try {
            ElectronicDocument document = transactions.execute(status -> {
                EpisodeAccount account = accounts.findByAdmissionNumber(collected.admissionNumber())
                        .orElseThrow(BillingException.AccountNotFound::new);
                return issuance.issue(Invoice.sharedPayment(account, InvoiceCommands.patientAsBuyer(patient),
                        InvoiceCommands.userOf(patient), kind, collected.amount(), authorization,
                        collected.collectionReference(), coverage.contractNumber()));
            });
            log.info("{} {} of {} invoiced to the patient as {} (collection {})", kind, collected.amount(),
                    collected.admissionNumber(), document.number(), collected.collectionReference());
            return new Invoiced(document, true);
        } catch (DataIntegrityViolationException raced) {
            Invoice concurrent = invoices.findByCollectionReference(collected.collectionReference()).orElseThrow(() -> raced);
            return new Invoiced(sameCollection(concurrent, collected), false);
        }
    }

    public ElectronicDocument document(java.util.UUID documentUuid) {
        return documents.findByUuid(documentUuid).orElseThrow(BillingException.InvoiceNotIssued::new);
    }

    public static SharedPaymentKind proposedKind(PatientDetails patient, EpisodeAccount account) {
        String regime = patient instanceof PatientDetails.Registered registered ? registered.healthRegime() : null;
        return SharedPaymentKind.proposedFor(regime, account.kind());
    }

    private ElectronicDocument sameCollection(Invoice earlier, Collected collected) {
        boolean same = earlier.account().admissionNumber().equals(collected.admissionNumber())
                && earlier.grossTotal().compareTo(Money.of(collected.amount())) == 0
                && Objects.equals(earlier.authorizationNumber(), blankToNull(collected.authorizationNumber()))
                && (collected.kind() == null || collected.kind() == earlier.sharedPaymentKind());
        if (!same) {
            throw new BillingException.CollectionReferenceReused();
        }
        return documents.ofInvoice(earlier.uuid()).orElseThrow(BillingException.InvoiceNotIssued::new);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

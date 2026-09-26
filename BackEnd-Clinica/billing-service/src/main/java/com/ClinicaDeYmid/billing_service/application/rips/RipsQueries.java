package com.ClinicaDeYmid.billing_service.application.rips;

import com.ClinicaDeYmid.billing_service.application.clinical.ClinicalFacts;
import com.ClinicaDeYmid.billing_service.application.context.EpisodeDetails;
import com.ClinicaDeYmid.billing_service.application.context.EpisodeDirectory;
import com.ClinicaDeYmid.billing_service.application.context.EpisodeLookup;
import com.ClinicaDeYmid.billing_service.application.context.PatientDetails;
import com.ClinicaDeYmid.billing_service.application.context.PatientDirectory;
import com.ClinicaDeYmid.billing_service.application.context.PatientLookup;
import com.ClinicaDeYmid.billing_service.application.sale.PractitionerDirectory;
import com.ClinicaDeYmid.billing_service.application.sale.PractitionerLookup;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccount;
import com.ClinicaDeYmid.billing_service.domain.Invoice;
import com.ClinicaDeYmid.billing_service.domain.InvoiceStatus;
import com.ClinicaDeYmid.billing_service.domain.Invoices;
import com.ClinicaDeYmid.billing_service.domain.Issuer;
import com.ClinicaDeYmid.billing_service.domain.Issuers;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

@Service
public class RipsQueries {

    private final Invoices invoices;
    private final Issuers issuers;
    private final EpisodeDirectory episodes;
    private final PatientDirectory patients;
    private final PractitionerDirectory practitioners;
    private final ClinicalFacts clinical;
    private final Clock clock;

    public RipsQueries(Invoices invoices, Issuers issuers, EpisodeDirectory episodes, PatientDirectory patients,
                       PractitionerDirectory practitioners, ClinicalFacts clinical, Clock clock) {
        this.invoices = invoices;
        this.issuers = issuers;
        this.episodes = episodes;
        this.patients = patients;
        this.practitioners = practitioners;
        this.clinical = clinical;
        this.clock = clock;
    }

    public RipsDraft draft(UUID invoiceUuid) {
        Invoice invoice = invoices.findByUuid(invoiceUuid).orElseThrow(BillingException.InvoiceNotFound::new);
        if (invoice.purpose() != Invoice.Purpose.SERVICES) {
            throw new BillingException.RipsNotApplicable();
        }
        if (!(invoice.status() instanceof InvoiceStatus.Issued) && !(invoice.status() instanceof InvoiceStatus.Voided)) {
            throw new BillingException.InvoiceNotIssued();
        }
        Issuer issuer = issuers.find().orElseThrow(BillingException.IssuerNotConfigured::new);
        EpisodeAccount account = invoice.account();
        EpisodeDetails episode = switch (episodes.episode(account.admissionUuid())) {
            case EpisodeLookup.Found found -> found.episode();
            case EpisodeLookup.NotFound ignored -> throw new BillingException.EpisodeUnknownToAdmissions();
            case EpisodeLookup.Unavailable ignored -> throw new BillingException.AdmissionsUnavailable();
        };
        PatientDetails patient = switch (patients.patient(account.patientUuid())) {
            case PatientLookup.Found found -> found.patient();
            case PatientLookup.NotFound ignored ->
                    throw new BillingException.BuyerNotIdentified("El paciente del episodio no está en el directorio");
            case PatientLookup.Unavailable ignored -> throw new BillingException.PatientsUnavailable();
        };
        RipsSources.Professional professional = null;
        if (episode.attending() != null && episode.attending().practitionerUuid() != null) {
            professional = switch (practitioners.practitioner(episode.attending().practitionerUuid())) {
                case PractitionerLookup.Found found -> new RipsSources.Professional(found.documentType(),
                        found.documentNumber());
                case PractitionerLookup.NotFound ignored -> null;
                case PractitionerLookup.Unavailable ignored -> throw new BillingException.PractitionersUnavailable();
            };
        }
        return RipsBuilder.build(new RipsSources(invoice, issuer, account, episode, patient, professional,
                clinical.ofAdmission(account.admissionUuid()), clock.getZone()));
    }
}

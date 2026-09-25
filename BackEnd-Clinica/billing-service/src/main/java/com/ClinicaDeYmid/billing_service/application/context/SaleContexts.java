package com.ClinicaDeYmid.billing_service.application.context;

import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccount;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccounts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SaleContexts {

    static final String PATIENT_UNAVAILABLE = "No se pudo consultar el directorio de pacientes; faltan los datos del paciente";
    static final String PATIENT_MISSING = "El paciente del episodio ya no aparece en el directorio de pacientes";
    static final String PAYER_UNAVAILABLE = "No se pudo consultar contratación; falta el nombre del pagador";
    static final String PAYER_MISSING = "El pagador de la cobertura ya no aparece en contratación";

    private static final Logger log = LoggerFactory.getLogger(SaleContexts.class);

    private final EpisodeAccounts accounts;
    private final EpisodeDirectory episodes;
    private final PatientDirectory patients;
    private final PayerDirectory payers;

    public SaleContexts(EpisodeAccounts accounts, EpisodeDirectory episodes, PatientDirectory patients,
                        PayerDirectory payers) {
        this.accounts = accounts;
        this.episodes = episodes;
        this.patients = patients;
        this.payers = payers;
    }

    public SaleContext forAdmission(String admissionNumber) {
        EpisodeAccount account = accounts.findByAdmissionNumber(admissionNumber)
                .orElseThrow(BillingException.AccountNotFound::new);
        EpisodeDetails episode = switch (episodes.episode(account.admissionUuid())) {
            case EpisodeLookup.Found found -> found.episode();
            case EpisodeLookup.NotFound ignored -> {
                log.error("Admission {} has an account but admissions does not know it", admissionNumber);
                throw new BillingException.EpisodeUnknownToAdmissions();
            }
            case EpisodeLookup.Unavailable ignored -> throw new BillingException.AdmissionsUnavailable();
        };
        List<String> warnings = new ArrayList<>();
        PatientDetails patient = switch (patients.patient(account.patientUuid())) {
            case PatientLookup.Found found -> found.patient();
            case PatientLookup.NotFound ignored -> {
                warnings.add(PATIENT_MISSING);
                yield null;
            }
            case PatientLookup.Unavailable ignored -> {
                warnings.add(PATIENT_UNAVAILABLE);
                yield null;
            }
        };
        PayerDetails payer = null;
        if (episode.coverage() != null && episode.coverage().payerUuid() != null) {
            payer = switch (payers.payer(episode.coverage().payerUuid())) {
                case PayerLookup.Found found -> found.payer();
                case PayerLookup.NotFound ignored -> {
                    warnings.add(PAYER_MISSING);
                    yield null;
                }
                case PayerLookup.Unavailable ignored -> {
                    warnings.add(PAYER_UNAVAILABLE);
                    yield null;
                }
            };
        }
        return new SaleContext(account, episode, patient, payer, warnings);
    }
}

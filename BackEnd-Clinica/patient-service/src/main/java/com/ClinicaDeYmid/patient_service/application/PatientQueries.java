package com.ClinicaDeYmid.patient_service.application;

import com.ClinicaDeYmid.patient_service.domain.IdentityDocument;
import com.ClinicaDeYmid.patient_service.domain.Patient;
import com.ClinicaDeYmid.patient_service.domain.PatientException;
import com.ClinicaDeYmid.patient_service.domain.Patients;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PatientQueries {

    static final int MIN_PREFIX_LENGTH = 2;
    static final int MAX_PREFIX_LENGTH = 100;

    private static final Pattern NAME_PREFIX = Pattern.compile("^\\p{L}[\\p{L} '.-]*$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final Patients patients;
    private final HealthProviderDirectory healthProviders;
    private final PatientHistory history;

    public PatientQueries(Patients patients, HealthProviderDirectory healthProviders, PatientHistory history) {
        this.patients = patients;
        this.healthProviders = healthProviders;
        this.history = history;
    }

    public PatientDetails get(UUID uuid) {
        Patient patient = patients.findByUuid(uuid).orElseThrow(PatientException.NotFound::new);
        HealthProviderLookup healthProvider = patient.affiliation().regime().hasHealthProvider()
                ? healthProviders.findByNit(patient.affiliation().healthProviderNit())
                : new HealthProviderLookup.NotAffiliated();
        return new PatientDetails(patient, healthProvider);
    }

    public Optional<Patient> findByDocument(IdentityDocument document) {
        return patients.findByDocument(document);
    }

    public Page<Patient> searchByName(String lastNames, String firstNames, Pageable pageable) {
        return patients.searchByName(new Patients.NameQuery(
                prefix(lastNames, "name.lastNames", true), prefix(firstNames, "name.firstNames", false)), pageable);
    }

    public List<PatientHistory.Revision> history(UUID uuid) {
        if (patients.findByUuid(uuid).isEmpty()) {
            throw new PatientException.NotFound();
        }
        return history.of(uuid);
    }

    private static String prefix(String value, String field, boolean required) {
        String text = value == null ? "" : WHITESPACE.matcher(value.strip()).replaceAll(" ");
        if (text.isEmpty() && !required) {
            return null;
        }
        if (text.length() < MIN_PREFIX_LENGTH || text.length() > MAX_PREFIX_LENGTH || !NAME_PREFIX.matcher(text).matches()) {
            throw new PatientException.InvalidData(field,
                    "debe tener entre " + MIN_PREFIX_LENGTH + " y " + MAX_PREFIX_LENGTH + " letras");
        }
        return text;
    }

    public record PatientDetails(Patient patient, HealthProviderLookup healthProvider) {
    }
}

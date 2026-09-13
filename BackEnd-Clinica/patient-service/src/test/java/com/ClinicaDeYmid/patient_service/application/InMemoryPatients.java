package com.ClinicaDeYmid.patient_service.application;

import com.ClinicaDeYmid.patient_service.domain.IdentityDocument;
import com.ClinicaDeYmid.patient_service.domain.Patient;
import com.ClinicaDeYmid.patient_service.domain.Patients;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class InMemoryPatients implements Patients {

    private final Map<UUID, Patient> store = new LinkedHashMap<>();
    private NameQuery lastSearch;

    @Override
    public Patient save(Patient patient) {
        store.put(patient.uuid(), patient);
        return patient;
    }

    @Override
    public Optional<Patient> findByUuid(UUID uuid) {
        return Optional.ofNullable(store.get(uuid));
    }

    @Override
    public Optional<Patient> findByDocument(IdentityDocument document) {
        return store.values().stream().filter(patient -> patient.document().equals(document)).findFirst();
    }

    @Override
    public boolean existsByDocument(IdentityDocument document) {
        return findByDocument(document).isPresent();
    }

    @Override
    public Page<Patient> searchByName(NameQuery query, Pageable pageable) {
        lastSearch = query;
        List<Patient> matches = store.values().stream()
                .filter(patient -> startsWith(patient.demographics().name().lastNames(), query.lastNamesPrefix())
                        && (query.firstNamesPrefix() == null || startsWith(patient.demographics().name().firstNames(), query.firstNamesPrefix())))
                .toList();
        return new PageImpl<>(matches, pageable, matches.size());
    }

    NameQuery lastSearch() {
        return lastSearch;
    }

    private static boolean startsWith(String value, String prefix) {
        return value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }
}

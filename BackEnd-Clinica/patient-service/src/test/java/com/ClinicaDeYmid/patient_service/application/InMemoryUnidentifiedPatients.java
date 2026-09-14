package com.ClinicaDeYmid.patient_service.application;

import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatient;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatientCode;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatients;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class InMemoryUnidentifiedPatients implements UnidentifiedPatients {

    private final Map<UUID, UnidentifiedPatient> store = new LinkedHashMap<>();
    private final Map<Integer, Long> sequences = new HashMap<>();

    @Override
    public UnidentifiedPatient save(UnidentifiedPatient patient) {
        store.put(patient.uuid(), patient);
        return patient;
    }

    @Override
    public Optional<UnidentifiedPatient> findByUuid(UUID uuid) {
        return Optional.ofNullable(store.get(uuid));
    }

    @Override
    public String nextCode(int year) {
        return UnidentifiedPatientCode.of(year, sequences.merge(year, 1L, Long::sum));
    }
}

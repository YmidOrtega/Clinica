package com.ClinicaDeYmid.patient_service.application;

import com.ClinicaDeYmid.patient_service.domain.PatientException;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatient;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatients;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UnidentifiedPatientQueries {

    private final UnidentifiedPatients unidentifiedPatients;

    public UnidentifiedPatientQueries(UnidentifiedPatients unidentifiedPatients) {
        this.unidentifiedPatients = unidentifiedPatients;
    }

    public UnidentifiedPatient get(UUID uuid) {
        return unidentifiedPatients.findByUuid(uuid).orElseThrow(PatientException.UnidentifiedNotFound::new);
    }
}

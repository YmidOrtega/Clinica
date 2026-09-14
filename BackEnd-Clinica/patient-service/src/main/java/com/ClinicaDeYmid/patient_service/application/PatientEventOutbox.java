package com.ClinicaDeYmid.patient_service.application;

import com.ClinicaDeYmid.patient_service.domain.Patient;
import com.ClinicaDeYmid.patient_service.domain.PatientEvent;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatient;
import com.ClinicaDeYmid.patient_service.domain.UnidentifiedPatientEvent;

import java.util.List;

public interface PatientEventOutbox {

    void append(Patient patient, List<PatientEvent> events);

    void appendUnidentified(UnidentifiedPatient patient, List<UnidentifiedPatientEvent> events);
}

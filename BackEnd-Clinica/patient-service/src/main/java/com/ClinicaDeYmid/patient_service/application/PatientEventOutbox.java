package com.ClinicaDeYmid.patient_service.application;

import com.ClinicaDeYmid.patient_service.domain.Patient;
import com.ClinicaDeYmid.patient_service.domain.PatientEvent;

import java.util.List;

public interface PatientEventOutbox {

    void append(Patient patient, List<PatientEvent> events);
}

package com.ClinicaDeYmid.admissions_service.application;

import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionEvent;

import java.util.List;

public interface AdmissionEventOutbox {

    void append(Admission admission, List<AdmissionEvent> events);
}

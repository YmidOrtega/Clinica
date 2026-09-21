package com.ClinicaDeYmid.admissions_service.application.receipt;

import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.Authorization;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;

import java.util.List;

public record ReceiptContent(Admission admission, PatientReference patient, List<Authorization> authorizations,
                             String issuedByName, String keyId) {
}

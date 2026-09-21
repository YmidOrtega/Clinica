package com.ClinicaDeYmid.admissions_service.application;

import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionSearch;
import com.ClinicaDeYmid.admissions_service.domain.Admissions;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReferences;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionsException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AdmissionQueries {

    private final Admissions admissions;
    private final PatientReferences patients;

    public AdmissionQueries(Admissions admissions, PatientReferences patients) {
        this.admissions = admissions;
        this.patients = patients;
    }

    public Admission get(UUID uuid) {
        return admissions.findByUuid(uuid).orElseThrow(AdmissionsException.AdmissionNotFound::new);
    }

    public Page<Admission> search(AdmissionSearch criteria, PatientReference.Document document, Pageable pageable) {
        if (document == null) {
            return admissions.search(criteria, pageable);
        }
        return patients.findByDocument(document.type(), document.number())
                .map(patient -> admissions.search(withPatient(criteria, patient.uuid()), pageable))
                .orElseGet(() -> Page.empty(pageable));
    }

    public Page<Admission> withPendingCoverage(Pageable pageable) {
        return admissions.findWithPendingCoverage(pageable);
    }

    public Page<Admission> withPendingDeathNotice(Pageable pageable) {
        return admissions.findWithPendingDeathNotice(pageable);
    }

    public List<Admission> queueOf(UUID configurationServiceUuid) {
        return admissions.findQueueOf(configurationServiceUuid);
    }

    private static AdmissionSearch withPatient(AdmissionSearch criteria, UUID patientUuid) {
        return new AdmissionSearch(patientUuid, criteria.number(), criteria.status(), criteria.kind(),
                criteria.configurationServiceUuid(), criteria.from(), criteria.to());
    }
}

package com.ClinicaDeYmid.admissions_service.application;

import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.Admissions;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AdmissionQueries {

    private final Admissions admissions;

    public AdmissionQueries(Admissions admissions) {
        this.admissions = admissions;
    }

    public Admission get(UUID uuid) {
        return admissions.findByUuid(uuid).orElseThrow(AdmissionsException.AdmissionNotFound::new);
    }

    public Admission byNumber(String number) {
        return admissions.findByNumber(number).orElseThrow(AdmissionsException.AdmissionNotFound::new);
    }

    public List<Admission> ofPatient(UUID patientUuid) {
        return admissions.findByPatient(patientUuid);
    }
}

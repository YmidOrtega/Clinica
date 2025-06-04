package com.ClinicaDeYmid.patient_service.module.patient.service;

import com.ClinicaDeYmid.patient_service.infra.exception.PatientNotActiveException;
import com.ClinicaDeYmid.patient_service.infra.exception.PatientNotFoundException;
import com.ClinicaDeYmid.patient_service.module.patient.dto.GetPatientDto;
import com.ClinicaDeYmid.patient_service.module.patient.mapper.PatientMapper;
import com.ClinicaDeYmid.patient_service.module.patient.entity.Patient;
import com.ClinicaDeYmid.patient_service.module.patient.entity.enums.Status;
import com.ClinicaDeYmid.patient_service.module.patient.repository.PatientRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GetPatientInformationService {

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;

    public GetPatientInformationService(PatientRepository patientRepository, PatientMapper patientMapper) {
        this.patientRepository = patientRepository;
        this.patientMapper = patientMapper;
    }

    @Transactional(readOnly = true)
    public GetPatientDto getPatientDto(String identification) {

        Patient patient = patientRepository.findByIdentification(identification)
                .orElseThrow(() -> new PatientNotFoundException(identification));

        if (patient.getStatus() != Status.ALIVE) {
            throw new PatientNotActiveException(patient.getStatus().getDisplayName());
        }

        return patientMapper.toPatientDTO(patient);
    }
}

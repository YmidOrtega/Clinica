package com.ClinicaDeYmid.patient_service.module.patient.service;

import com.ClinicaDeYmid.patient_service.infra.exception.PatientDataAccessException;
import com.ClinicaDeYmid.patient_service.infra.exception.PatientNotActiveException;
import com.ClinicaDeYmid.patient_service.infra.exception.PatientNotFoundException;
import com.ClinicaDeYmid.patient_service.module.patient.dto.GetPatientDto;
import com.ClinicaDeYmid.patient_service.module.patient.mapper.PatientMapper;
import com.ClinicaDeYmid.patient_service.module.patient.entity.Patient;
import com.ClinicaDeYmid.patient_service.module.patient.entity.enums.Status;
import com.ClinicaDeYmid.patient_service.module.patient.repository.PatientRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Service
public class GetPatientInformationService {

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;

    public GetPatientInformationService(PatientRepository patientRepository, PatientMapper patientMapper) {
        this.patientRepository = patientRepository;
        this.patientMapper = patientMapper;
    }

    @Transactional(readOnly = true)
    public GetPatientDto getPatientDto(String identificationNumber) {

        try {
            Patient patient = patientRepository.findByIdentificationNumber(identificationNumber)
                    .orElseThrow(() -> new PatientNotFoundException(identificationNumber));

            if (patient.getStatus() != Status.ALIVE) {
                throw new PatientNotActiveException(patient.getStatus().getDisplayName());
            }

            return patientMapper.toPatientDTO(patient);

        } catch (DataAccessException ex) {
            throw new PatientDataAccessException("obtener información del paciente", ex);
        }
    }

}

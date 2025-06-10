package com.ClinicaDeYmid.patient_service.module.service;

import com.ClinicaDeYmid.patient_service.infra.exception.PatientDataAccessException;
import com.ClinicaDeYmid.patient_service.infra.exception.PatientNotActiveException;
import com.ClinicaDeYmid.patient_service.infra.exception.PatientNotFoundException;
import com.ClinicaDeYmid.patient_service.module.controller.HealthProviderClient;
import com.ClinicaDeYmid.patient_service.module.dto.GetPatientDto;
import com.ClinicaDeYmid.patient_service.module.mapper.PatientMapper;
import com.ClinicaDeYmid.patient_service.module.entity.Patient;
import com.ClinicaDeYmid.patient_service.module.enums.Status;
import com.ClinicaDeYmid.patient_service.module.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetPatientInformationService {

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;
    private final HealthProviderClient healthProviderClient;

    @Transactional(readOnly = true)
    public GetPatientDto getPatientDto(String identificationNumber) {

        try {
            Patient patient = patientRepository.findByIdentificationNumber(identificationNumber)
                    .orElseThrow(() -> new PatientNotFoundException(identificationNumber));

            if (patient.getStatus() != Status.ALIVE) {
                throw new PatientNotActiveException(patient.getStatus().getDisplayName());
            }

            HealthProviderDto provider = healthProviderClient.getHealthProviderByNit(patient.getHealthProviderNit());

            return patientMapper.toPatientDTO(patient);

        } catch (DataAccessException ex) {
            throw new PatientDataAccessException("obtener información del paciente", ex);
        }
    }

}

package com.ClinicaDeYmid.patient_service.module.service;

import com.ClinicaDeYmid.patient_service.infra.exception.PatientDataAccessException;
import com.ClinicaDeYmid.patient_service.infra.exception.PatientNotFoundException;
import com.ClinicaDeYmid.patient_service.module.dto.GetHealthProviderDto;
import com.ClinicaDeYmid.patient_service.module.dto.PatientResponseDto;
import com.ClinicaDeYmid.patient_service.module.dto.UpdatePatientDto;
import com.ClinicaDeYmid.patient_service.module.entity.Patient;
import com.ClinicaDeYmid.patient_service.module.feignclient.HealthProviderClient;
import com.ClinicaDeYmid.patient_service.module.mapper.PatientMapper;
import com.ClinicaDeYmid.patient_service.module.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdatePatientInformationService {

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;
    private final HealthProviderClient healthProviderClient;

    @Transactional
    public PatientResponseDto updatePatientInformation(UpdatePatientDto updatePatientDto, String identification) {

        try {

            Patient patient = patientRepository.findByIdentificationNumber(identification)
                    .orElseThrow(() -> new PatientNotFoundException(identification));

            GetHealthProviderDto provider = healthProviderClient.getHealthProviderByNit(patient.getHealthProviderNit());

            patientMapper.updatePatientFromDTO(updatePatientDto, patient);

            Patient updatedPatient = patientRepository.save(patient);

            return patientMapper.toPatientResponseDto(updatedPatient, provider);

        } catch (DataAccessException ex) {
            throw new PatientDataAccessException("actualizar información del paciente", ex);
        }
    }
}

package com.ClinicaDeYmid.patient_service.module.service;

import com.ClinicaDeYmid.patient_service.infra.exception.PatientDataAccessException;
import com.ClinicaDeYmid.patient_service.infra.exception.PatientNotFoundException;
import com.ClinicaDeYmid.patient_service.module.dto.HealthProviderNitDto;
import com.ClinicaDeYmid.patient_service.module.dto.patient.PatientResponseDto;
import com.ClinicaDeYmid.patient_service.module.dto.patient.UpdatePatientDto;
import com.ClinicaDeYmid.patient_service.module.entity.Patient;
import com.ClinicaDeYmid.patient_service.module.feignclient.HealthProviderClient;
import com.ClinicaDeYmid.patient_service.module.mapper.PatientMapper;
import com.ClinicaDeYmid.patient_service.module.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdatePatientInformationService {

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;
    private final HealthProviderClient healthProviderClient;

    @CacheEvict(value = "patient-entities", key = "#identification")
    @Transactional
    public PatientResponseDto updatePatientInformation(UpdatePatientDto updatePatientDto, String identification) {
        log.info("Updating patient with identification: {}", identification);
        log.debug("Invalidando cache para patient: {}", identification);

        try {
            Patient patient = patientRepository.findByIdentificationNumber(identification)
                    .orElseThrow(() -> new PatientNotFoundException(identification));

            HealthProviderNitDto provider = healthProviderClient
                    .getHealthProviderByNit(patient.getHealthProviderNit());

            patientMapper.updatePatientFromDto(updatePatientDto, patient);

            Patient updatedPatient = patientRepository.save(patient);
            log.info("Patient updated successfully with ID: {}", updatedPatient.getId());

            return patientMapper.toPatientResponseDto(updatedPatient, provider);

        } catch (DataAccessException ex) {
            throw new PatientDataAccessException("actualizar información del paciente", ex);
        }
    }
}
package com.ClinicaDeYmid.patient_service.module.service;

import com.ClinicaDeYmid.patient_service.infra.exception.PatientAlreadyExistsException;
import com.ClinicaDeYmid.patient_service.infra.exception.PatientDataAccessException;
import com.ClinicaDeYmid.patient_service.module.dto.HealthProviderNitDto;
import com.ClinicaDeYmid.patient_service.module.dto.NewPatientDto;
import com.ClinicaDeYmid.patient_service.module.dto.patient.PatientResponseDto;
import com.ClinicaDeYmid.patient_service.module.feignclient.HealthProviderClient;
import com.ClinicaDeYmid.patient_service.module.mapper.PatientMapper;
import com.ClinicaDeYmid.patient_service.module.entity.Patient;
import com.ClinicaDeYmid.patient_service.module.repository.PatientRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatientRecordService {

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;
    private final HealthProviderClient healthProviderClient;

    @Transactional
    public PatientResponseDto createPatient(@Valid NewPatientDto newPatientDto) {
        log.info("Creating new patient with identification: {}", newPatientDto.identificationNumber());

        try {
            // Validar que no exista
            if (patientRepository.existsByIdentificationNumber(newPatientDto.identificationNumber())) {
                throw new PatientAlreadyExistsException(newPatientDto.identificationNumber());
            }

            // Crear entidad
            Patient newPatient = patientMapper.toPatient(newPatientDto);

            // Validar health provider en clients-service
            log.debug("Fetching health provider: {}", newPatientDto.healthProviderNit());
            HealthProviderNitDto provider = healthProviderClient
                    .getHealthProviderByNit(newPatientDto.healthProviderNit());

            // Guardar en DB
            Patient savedPatient = patientRepository.save(newPatient);
            log.info("Patient saved successfully with ID: {}", savedPatient.getId());

            // Construir DTO con datos frescos (NO se cachea)
            return patientMapper.toPatientResponseDto(savedPatient, provider);

        } catch (DataAccessException ex) {
            throw new PatientDataAccessException("crear paciente", ex);
        }
    }
}
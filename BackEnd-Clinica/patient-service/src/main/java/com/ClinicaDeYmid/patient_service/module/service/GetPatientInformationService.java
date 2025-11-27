package com.ClinicaDeYmid.patient_service.module.service;

import com.ClinicaDeYmid.patient_service.infra.exception.PatientDataAccessException;
import com.ClinicaDeYmid.patient_service.infra.exception.PatientNotActiveException;
import com.ClinicaDeYmid.patient_service.infra.exception.PatientNotFoundException;
import com.ClinicaDeYmid.patient_service.module.dto.HealthProviderNitDto;
import com.ClinicaDeYmid.patient_service.module.feignclient.HealthProviderClient;
import com.ClinicaDeYmid.patient_service.module.dto.patient.GetPatientDto;
import com.ClinicaDeYmid.patient_service.module.mapper.PatientMapper;
import com.ClinicaDeYmid.patient_service.module.entity.Patient;
import com.ClinicaDeYmid.patient_service.module.enums.Status;
import com.ClinicaDeYmid.patient_service.module.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetPatientInformationService {

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;
    private final HealthProviderClient healthProviderClient;

    @Cacheable(value = "patient-entities", key = "#identificationNumber")
    @Transactional(readOnly = true)
    public Patient findEntityByIdentificationNumber(String identificationNumber) {
        log.debug("🔍 Cache MISS - Consultando DB para patient: {}", identificationNumber);

        try {
            Patient patient = patientRepository.findByIdentificationNumber(identificationNumber)
                    .orElseThrow(() -> new PatientNotFoundException(identificationNumber));

            if (patient.getStatus() != Status.ALIVE) {
                throw new PatientNotActiveException(patient.getStatus().getDisplayName());
            }

            return patient;

        } catch (DataAccessException ex) {
            throw new PatientDataAccessException("obtener entidad del paciente", ex);
        }
    }

    @Transactional(readOnly = true)
    public GetPatientDto getPatientDto(String identificationNumber) {
        log.debug("📦 Construyendo GetPatientDto completo para patient: {}", identificationNumber);

        try {

            Patient patient = findEntityByIdentificationNumber(identificationNumber);

            HealthProviderNitDto provider = healthProviderClient
                    .getHealthProviderByNit(patient.getHealthProviderNit());

            return patientMapper.toGetPatientDto(patient, provider);

        } catch (DataAccessException ex) {
            throw new PatientDataAccessException("obtener información del paciente", ex);
        }
    }
}
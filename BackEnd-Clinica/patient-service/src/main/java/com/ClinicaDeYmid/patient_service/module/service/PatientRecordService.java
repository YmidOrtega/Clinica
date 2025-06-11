package com.ClinicaDeYmid.patient_service.module.service;

import clients_patients.dto.HealthProviderResponseDto;
import com.ClinicaDeYmid.patient_service.infra.exception.PatientAlreadyExistsException;
import com.ClinicaDeYmid.patient_service.infra.exception.PatientDataAccessException;
import com.ClinicaDeYmid.patient_service.module.dto.GetClientDto;
import com.ClinicaDeYmid.patient_service.module.dto.NewPatientDto;
import com.ClinicaDeYmid.patient_service.module.dto.PatientResponseDto;
import com.ClinicaDeYmid.patient_service.module.feignclient.HealthProviderClient;
import com.ClinicaDeYmid.patient_service.module.mapper.PatientMapper;
import com.ClinicaDeYmid.patient_service.module.entity.Patient;
import com.ClinicaDeYmid.patient_service.module.repository.PatientRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PatientRecordService {

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;
    private final HealthProviderClient healthProviderClient;

    @Transactional
    public PatientResponseDto createPatient(@Valid NewPatientDto newPatientDto) {

        try {

            if (patientRepository.existsByIdentificationNumber(newPatientDto.identificationNumber())) {
                throw new PatientAlreadyExistsException(newPatientDto.identificationNumber());
            }

            Patient newPatient = patientMapper.toPatient(newPatientDto);

            HealthProviderResponseDto provider = healthProviderClient.getHealthProviderByNit(newPatient.getHealthProviderNit());

            patientRepository.save(newPatient);

            return patientMapper.toPatientResponseDto(newPatient, provider);

    }catch (DataAccessException ex) {
            throw new PatientDataAccessException("crear paciente", ex);
        }
    }


}

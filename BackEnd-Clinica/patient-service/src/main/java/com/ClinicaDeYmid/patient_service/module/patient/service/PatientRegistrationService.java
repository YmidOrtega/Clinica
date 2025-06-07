package com.ClinicaDeYmid.patient_service.module.patient.service;

import com.ClinicaDeYmid.patient_service.infra.exception.PatientAlreadyExistsException;
import com.ClinicaDeYmid.patient_service.infra.exception.PatientDataAccessException;
import com.ClinicaDeYmid.patient_service.module.patient.dto.NewPatientDto;
import com.ClinicaDeYmid.patient_service.module.patient.dto.PatientResponseDto;
import com.ClinicaDeYmid.patient_service.module.patient.mapper.PatientMapper;
import com.ClinicaDeYmid.patient_service.module.patient.entity.Patient;
import com.ClinicaDeYmid.patient_service.module.patient.repository.PatientRepository;
import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientRegistrationService {

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;

    public PatientRegistrationService(PatientMapper patientMapper, PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
        this.patientMapper = patientMapper;

    }

    @Transactional
    public PatientResponseDto createPatient(@Valid NewPatientDto newPatientDto) {

        try {

            if (patientRepository.existsByIdentificationNumber(newPatientDto.identificationNumber())) {
                throw new PatientAlreadyExistsException(newPatientDto.identificationNumber());
            }

        Patient newPatient = patientMapper.toPatient(newPatientDto);
        patientRepository.save(newPatient);

        return new PatientResponseDto(
                newPatient.getUuid(),
                newPatient.getName(),
                newPatient.getLastName(),
                newPatient.getIdentificationNumber(),
                newPatient.getEmail(),
                newPatient.getCreatedAt()//,
                //newPatient.getHealthProviderId()
                );
    }catch (DataAccessException ex) {
            throw new PatientDataAccessException("crear paciente", ex);
        }
    }


}

package com.ClinicaDeYmid.patient_service.module.patient.service;

import com.ClinicaDeYmid.patient_service.module.patient.dto.NewPatientDto;
import com.ClinicaDeYmid.patient_service.module.patient.dto.PatientResponseDto;
import com.ClinicaDeYmid.patient_service.module.patient.mapper.PatientMapper;
import com.ClinicaDeYmid.patient_service.module.patient.entity.Patient;
import com.ClinicaDeYmid.patient_service.module.patient.repository.PatientRepository;
import jakarta.validation.Valid;
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
    public PatientResponseDto createPatient(@Valid NewPatientDto newPatientDTO) {

        System.out.println(newPatientDTO.dateOfBirth());

        Patient newPatient = patientMapper.toPatient(newPatientDTO);
        patientRepository.save(newPatient);

        return new PatientResponseDto(
                newPatient.getUuid(),
                newPatient.getName(),
                newPatient.getLastName(),
                newPatient.getIdentification(),
                newPatient.getEmail(),
                newPatient.getCreatedAt()//,
                //newPatient.getHealthPolicyId()
                );
    }


}

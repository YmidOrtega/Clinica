package com.ClinicaDeYmid.patient_service.module.patient.service;

import com.ClinicaDeYmid.patient_service.infra.exception.PatientNotFoundException;
import com.ClinicaDeYmid.patient_service.module.patient.dto.NewPatientDto;
import com.ClinicaDeYmid.patient_service.module.patient.dto.PatientResponseDto;
import com.ClinicaDeYmid.patient_service.module.patient.dto.UpdatePatientDto;
import com.ClinicaDeYmid.patient_service.module.patient.entity.Patient;
import com.ClinicaDeYmid.patient_service.module.patient.mapper.PatientMapper;
import com.ClinicaDeYmid.patient_service.module.patient.repository.PatientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UpdatePatientInformationService {

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;

    public UpdatePatientInformationService(PatientRepository patientRepository, PatientMapper patientMapper) {
        this.patientRepository = patientRepository;
        this.patientMapper = patientMapper;
    }

    @Transactional
    public PatientResponseDto updatePatientInformation(UpdatePatientDto updatePatientDto, String identification) {

        Patient patient = patientRepository.findByIdentification(identification)
                .orElseThrow(() -> new PatientNotFoundException(identification));

        patientMapper.updatePatientFromDTO(updatePatientDto, patient);

        Patient updatedPatient = patientRepository.save(patient);

        return new PatientResponseDto(
                updatedPatient.getUuid(),
                updatedPatient.getName(),
                updatedPatient.getLastName(),
                updatedPatient.getIdentification(),
                updatedPatient.getEmail(),
                updatedPatient.getCreatedAt()
        );


    }
}

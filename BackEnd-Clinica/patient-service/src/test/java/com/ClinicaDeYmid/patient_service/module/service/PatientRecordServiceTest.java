package com.ClinicaDeYmid.patient_service.module.service;

import com.ClinicaDeYmid.patient_service.infra.exception.PatientAlreadyExistsException;
import com.ClinicaDeYmid.patient_service.infra.exception.PatientDataAccessException;
import com.ClinicaDeYmid.patient_service.module.dto.HealthProviderNitDto;
import com.ClinicaDeYmid.patient_service.module.dto.NewPatientDto;
import com.ClinicaDeYmid.patient_service.module.dto.patient.PatientResponseDto;
import com.ClinicaDeYmid.patient_service.module.entity.Occupation;
import com.ClinicaDeYmid.patient_service.module.entity.Patient;
import com.ClinicaDeYmid.patient_service.module.entity.Site;
import com.ClinicaDeYmid.patient_service.module.enums.*;
import com.ClinicaDeYmid.patient_service.module.feignclient.HealthProviderClient;
import com.ClinicaDeYmid.patient_service.module.mapper.PatientMapper;
import com.ClinicaDeYmid.patient_service.module.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientRecordServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PatientMapper patientMapper;

    @Mock
    private HealthProviderClient healthProviderClient;

    @InjectMocks
    private PatientRecordService patientRecordService;

    private NewPatientDto newPatientDto;
    private Patient patient;
    private HealthProviderNitDto healthProviderNitDto;
    private PatientResponseDto patientResponseDto;

    @BeforeEach
    void setUp() {
        Site site = new Site();
        site.setCity("Bogota");

        Occupation occupation = new Occupation();
        occupation.setName("Engineer");

        newPatientDto = new NewPatientDto(
                null,
                IdentificationType.CEDULA_DE_CIUDADANIA,
                "123456789",
                "John",
                "Doe",
                LocalDate.of(1990, 1, 1),
                site,
                site,
                Disability.NONE,
                Language.SPANISH,
                Gender.MASCULINE,
                occupation,
                MaritalStatus.SINGLE,
                Religion.CATHOLIC,
                TypeOfAffiliation.CONTRIBUTOR,
                "AFF123",
                "NIT800",
                "POL123",
                "Mother",
                "Father",
                Zone.URBAN,
                site,
                "Address 123",
                "5551234",
                "3001234567",
                "john@example.com"
        );

        patient = new Patient();
        patient.setId(1L);
        patient.setIdentificationNumber("123456789");

        healthProviderNitDto = new HealthProviderNitDto(
                "NIT800", 
                "HealthProvider", 
                "EPS", 
                java.util.Collections.emptyList(), 
                "VIGENTE" // Assuming contractStatus is string
        );
        
        com.ClinicaDeYmid.patient_service.module.dto.GetClientDto getClientDto = 
            new com.ClinicaDeYmid.patient_service.module.dto.GetClientDto("HealthProvider", "EPS");

        patientResponseDto = new PatientResponseDto(
                "uuid-123",
                "John",
                "Doe",
                "123456789",
                "john@example.com",
                java.time.LocalDateTime.now(),
                getClientDto
        );
    }

    @Test
    void createPatient_Success() {
        // Arrange
        when(patientRepository.existsByIdentificationNumber(newPatientDto.identificationNumber())).thenReturn(false);
        when(patientMapper.toPatient(newPatientDto)).thenReturn(patient);
        when(healthProviderClient.getHealthProviderByNit(newPatientDto.healthProviderNit())).thenReturn(healthProviderNitDto);
        when(patientRepository.save(patient)).thenReturn(patient);
        when(patientMapper.toPatientResponseDto(patient, healthProviderNitDto)).thenReturn(patientResponseDto);

        // Act
        PatientResponseDto result = patientRecordService.createPatient(newPatientDto);

        // Assert
        assertNotNull(result);
        assertEquals(patientResponseDto.identificationNumber(), result.identificationNumber());
        verify(patientRepository).save(patient);
    }

    @Test
    void createPatient_AlreadyExists_ThrowsException() {
        // Arrange
        when(patientRepository.existsByIdentificationNumber(newPatientDto.identificationNumber())).thenReturn(true);

        // Act & Assert
        assertThrows(PatientAlreadyExistsException.class, () -> patientRecordService.createPatient(newPatientDto));
        verify(patientRepository, never()).save(any());
    }

    @Test
    void createPatient_DataAccessException_ThrowsException() {
        // Arrange
        when(patientRepository.existsByIdentificationNumber(newPatientDto.identificationNumber())).thenReturn(false);
        when(patientMapper.toPatient(newPatientDto)).thenReturn(patient);
        when(healthProviderClient.getHealthProviderByNit(newPatientDto.healthProviderNit())).thenReturn(healthProviderNitDto);
        when(patientRepository.save(patient)).thenThrow(new DataAccessException("DB Error") {});

        // Act & Assert
        assertThrows(PatientDataAccessException.class, () -> patientRecordService.createPatient(newPatientDto));
    }
}
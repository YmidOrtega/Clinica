package com.ClinicaDeYmid.patient_service.module.service;

import com.ClinicaDeYmid.patient_service.infra.exception.PatientDataAccessException;
import com.ClinicaDeYmid.patient_service.infra.exception.PatientNotActiveException;
import com.ClinicaDeYmid.patient_service.infra.exception.PatientNotFoundException;
import com.ClinicaDeYmid.patient_service.module.dto.HealthProviderNitDto;
import com.ClinicaDeYmid.patient_service.module.dto.patient.GetPatientDto;
import com.ClinicaDeYmid.patient_service.module.entity.Patient;
import com.ClinicaDeYmid.patient_service.module.enums.Status;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetPatientInformationServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PatientMapper patientMapper;

    @Mock
    private HealthProviderClient healthProviderClient;

    @InjectMocks
    private GetPatientInformationService getPatientInformationService;

    private Patient patient;
    private HealthProviderNitDto healthProviderNitDto;
    private GetPatientDto getPatientDto;

    @BeforeEach
    void setUp() {
        patient = new Patient();
        patient.setIdentificationNumber("123456789");
        patient.setStatus(Status.ALIVE);
        patient.setHealthProviderNit("NIT800");

        healthProviderNitDto = new HealthProviderNitDto(
                "NIT800", 
                "HealthProvider", 
                "EPS", 
                java.util.Collections.emptyList(), 
                "Active"
        );
        
        // Assuming GetPatientDto is a record or class with a builder/constructor
        // Since I don't have the full definition, I'll mock the return of the mapper
        getPatientDto = mock(GetPatientDto.class);
    }

    @Test
    void findEntityByIdentificationNumber_Success() {
        // Arrange
        when(patientRepository.findByIdentificationNumber("123456789")).thenReturn(Optional.of(patient));

        // Act
        Patient result = getPatientInformationService.findEntityByIdentificationNumber("123456789");

        // Assert
        assertNotNull(result);
        assertEquals("123456789", result.getIdentificationNumber());
    }

    @Test
    void findEntityByIdentificationNumber_NotFound_ThrowsException() {
        // Arrange
        when(patientRepository.findByIdentificationNumber("999")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(PatientNotFoundException.class, () -> getPatientInformationService.findEntityByIdentificationNumber("999"));
    }

    @Test
    void findEntityByIdentificationNumber_NotActive_ThrowsException() {
        // Arrange
        patient.setStatus(Status.DECEASED); // Assuming this is not ALIVE
        when(patientRepository.findByIdentificationNumber("123456789")).thenReturn(Optional.of(patient));

        // Act & Assert
        assertThrows(PatientNotActiveException.class, () -> getPatientInformationService.findEntityByIdentificationNumber("123456789"));
    }

    @Test
    void getPatientDto_Success() {
        // Arrange
        when(patientRepository.findByIdentificationNumber("123456789")).thenReturn(Optional.of(patient));
        when(healthProviderClient.getHealthProviderByNit("NIT800")).thenReturn(healthProviderNitDto);
        when(patientMapper.toGetPatientDto(patient, healthProviderNitDto)).thenReturn(getPatientDto);

        // Act
        GetPatientDto result = getPatientInformationService.getPatientDto("123456789");

        // Assert
        assertNotNull(result);
        verify(healthProviderClient).getHealthProviderByNit("NIT800");
    }
    
    @Test
    void getPatientDto_DataAccessException_ThrowsException() {
        // Arrange
         when(patientRepository.findByIdentificationNumber("123456789")).thenThrow(new DataAccessException("DB Error") {});

        // Act & Assert
        assertThrows(PatientDataAccessException.class, () -> getPatientInformationService.getPatientDto("123456789"));
    }
}

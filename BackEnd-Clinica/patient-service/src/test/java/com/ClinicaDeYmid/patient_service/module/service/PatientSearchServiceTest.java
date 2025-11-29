package com.ClinicaDeYmid.patient_service.module.service;

import com.ClinicaDeYmid.patient_service.infra.exception.InvalidSearchParametersException;
import com.ClinicaDeYmid.patient_service.infra.exception.PatientDataAccessException;
import com.ClinicaDeYmid.patient_service.infra.exception.PatientSearchNoResultsException;
import com.ClinicaDeYmid.patient_service.module.dto.patient.PatientsListDto;
import com.ClinicaDeYmid.patient_service.module.entity.Patient;
import com.ClinicaDeYmid.patient_service.module.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PatientSearchService Unit Tests")
class PatientSearchServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @InjectMocks
    private PatientSearchService service;

    private Patient patient1;
    private Patient patient2;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        patient1 = new Patient();
        patient1.setId(1L);
        patient1.setIdentificationNumber("123456789");
        patient1.setName("John");
        patient1.setLastName("Doe");

        patient2 = new Patient();
        patient2.setId(2L);
        patient2.setIdentificationNumber("987654321");
        patient2.setName("Jane");
        patient2.setLastName("Smith");

        pageable = PageRequest.of(0, 10);
    }

    @Test
    @DisplayName("searchPatients - Should return paginated results successfully")
    void searchPatients_Success() {
        // Arrange
        String query = "John";
        List<Patient> patients = Arrays.asList(patient1, patient2);
        Page<Patient> patientPage = new PageImpl<>(patients, pageable, patients.size());

        when(patientRepository.searchPatients(anyString(), any(Pageable.class)))
                .thenReturn(patientPage);

        // Act
        Page<PatientsListDto> result = service.searchPatients(query, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());
        assertEquals("123456789", result.getContent().get(0).identificationNumber());
        assertEquals("John", result.getContent().get(0).name());
        assertEquals("Doe", result.getContent().get(0).lastName());

        verify(patientRepository, times(1)).searchPatients(query.toLowerCase().trim(), pageable);
    }

    @Test
    @DisplayName("searchPatients - Should throw InvalidSearchParametersException when query is too short")
    void searchPatients_QueryTooShort() {
        // Arrange
        String shortQuery = "J";

        // Act & Assert
        InvalidSearchParametersException exception = assertThrows(
                InvalidSearchParametersException.class,
                () -> service.searchPatients(shortQuery, pageable)
        );

        assertEquals("Parámetro de búsqueda inválido - query: debe tener al menos 2 caracteres", exception.getMessage());
        verify(patientRepository, never()).searchPatients(anyString(), any(Pageable.class));
    }

    @Test
    @DisplayName("searchPatients - Should throw InvalidSearchParametersException when page size is too large")
    void searchPatients_PageSizeTooLarge() {
        // Arrange
        String query = "John";
        Pageable largePageable = PageRequest.of(0, 150);

        // Act & Assert
        InvalidSearchParametersException exception = assertThrows(
                InvalidSearchParametersException.class,
                () -> service.searchPatients(query, largePageable)
        );

        assertEquals("Parámetro de búsqueda inválido - size: no puede ser mayor a 100", exception.getMessage());
        verify(patientRepository, never()).searchPatients(anyString(), any(Pageable.class));
    }

    @Test
    @DisplayName("searchPatients - Should throw PatientSearchNoResultsException when no results found")
    void searchPatients_NoResults() {
        // Arrange
        String query = "NonExistent";
        Page<Patient> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(patientRepository.searchPatients(anyString(), any(Pageable.class)))
                .thenReturn(emptyPage);

        // Act & Assert
        assertThrows(
                PatientSearchNoResultsException.class,
                () -> service.searchPatients(query, pageable)
        );

        verify(patientRepository, times(1)).searchPatients(query.toLowerCase().trim(), pageable);
    }

    @Test
    @DisplayName("searchPatients - Should handle query with extra whitespace")
    void searchPatients_QueryWithWhitespace() {
        // Arrange
        String query = "  John  ";
        List<Patient> patients = Collections.singletonList(patient1);
        Page<Patient> patientPage = new PageImpl<>(patients, pageable, patients.size());

        when(patientRepository.searchPatients(anyString(), any(Pageable.class)))
                .thenReturn(patientPage);

        // Act
        Page<PatientsListDto> result = service.searchPatients(query, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(patientRepository, times(1)).searchPatients("john", pageable);
    }

    @Test
    @DisplayName("searchPatients - Should convert query to lowercase")
    void searchPatients_UppercaseQuery() {
        // Arrange
        String query = "JOHN";
        List<Patient> patients = Collections.singletonList(patient1);
        Page<Patient> patientPage = new PageImpl<>(patients, pageable, patients.size());

        when(patientRepository.searchPatients(anyString(), any(Pageable.class)))
                .thenReturn(patientPage);

        // Act
        Page<PatientsListDto> result = service.searchPatients(query, pageable);

        // Assert
        assertNotNull(result);
        verify(patientRepository, times(1)).searchPatients("john", pageable);
    }

    @Test
    @DisplayName("searchPatients - Should throw PatientDataAccessException on database error")
    void searchPatients_DatabaseError() {
        // Arrange
        String query = "John";

        when(patientRepository.searchPatients(anyString(), any(Pageable.class)))
                .thenThrow(new DataAccessException("Database error") {});

        // Act & Assert
        PatientDataAccessException exception = assertThrows(
                PatientDataAccessException.class,
                () -> service.searchPatients(query, pageable)
        );

        assertTrue(exception.getMessage().contains("buscar pacientes"));
        verify(patientRepository, times(1)).searchPatients(query.toLowerCase().trim(), pageable);
    }

    @Test
    @DisplayName("searchPatients - Should handle minimum valid query length")
    void searchPatients_MinimumQueryLength() {
        // Arrange
        String query = "Jo";
        List<Patient> patients = Collections.singletonList(patient1);
        Page<Patient> patientPage = new PageImpl<>(patients, pageable, patients.size());

        when(patientRepository.searchPatients(anyString(), any(Pageable.class)))
                .thenReturn(patientPage);

        // Act
        Page<PatientsListDto> result = service.searchPatients(query, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(patientRepository, times(1)).searchPatients("jo", pageable);
    }

    @Test
    @DisplayName("searchPatients - Should handle maximum valid page size")
    void searchPatients_MaximumPageSize() {
        // Arrange
        String query = "John";
        Pageable maxPageable = PageRequest.of(0, 100);
        List<Patient> patients = Collections.singletonList(patient1);
        Page<Patient> patientPage = new PageImpl<>(patients, maxPageable, patients.size());

        when(patientRepository.searchPatients(anyString(), any(Pageable.class)))
                .thenReturn(patientPage);

        // Act
        Page<PatientsListDto> result = service.searchPatients(query, maxPageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(patientRepository, times(1)).searchPatients("john", maxPageable);
    }

    @Test
    @DisplayName("searchPatients - Should return correct DTO mapping")
    void searchPatients_CorrectDTOMapping() {
        // Arrange
        String query = "John";
        List<Patient> patients = Collections.singletonList(patient1);
        Page<Patient> patientPage = new PageImpl<>(patients, pageable, patients.size());

        when(patientRepository.searchPatients(anyString(), any(Pageable.class)))
                .thenReturn(patientPage);

        // Act
        Page<PatientsListDto> result = service.searchPatients(query, pageable);

        // Assert
        assertNotNull(result);
        PatientsListDto dto = result.getContent().get(0);
        assertEquals(patient1.getIdentificationNumber(), dto.identificationNumber());
        assertEquals(patient1.getName(), dto.name());
        assertEquals(patient1.getLastName(), dto.lastName());
    }

    @Test
    @DisplayName("searchPatients - Should handle pagination correctly")
    void searchPatients_PaginationCorrect() {
        // Arrange
        String query = "John";
        Pageable secondPage = PageRequest.of(1, 5);
        List<Patient> patients = Collections.singletonList(patient2);
        Page<Patient> patientPage = new PageImpl<>(patients, secondPage, 10);

        when(patientRepository.searchPatients(anyString(), any(Pageable.class)))
                .thenReturn(patientPage);

        // Act
        Page<PatientsListDto> result = service.searchPatients(query, secondPage);

        // Assert
        assertNotNull(result);
        assertEquals(10, result.getTotalElements());
        assertEquals(1, result.getNumberOfElements());
        assertEquals(1, result.getNumber());
        verify(patientRepository, times(1)).searchPatients("john", secondPage);
    }
}

package com.ClinicaDeYmid.clients_service.module.service;

import com.ClinicaDeYmid.clients_service.infra.exception.DuplicateContractNumberException;
import com.ClinicaDeYmid.clients_service.infra.exception.DuplicateHealthProviderNitException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderDataAccessException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderValidationException;
import com.ClinicaDeYmid.clients_service.module.domain.Nit;
import com.ClinicaDeYmid.clients_service.module.dto.CreateHealthProviderDto;
import com.ClinicaDeYmid.clients_service.module.entity.Contract;
import com.ClinicaDeYmid.clients_service.module.entity.HealthProvider;
import com.ClinicaDeYmid.clients_service.module.enums.TypeProvider;
import com.ClinicaDeYmid.clients_service.module.mapper.HealthProviderMapper;
import com.ClinicaDeYmid.clients_service.module.repository.ContractRepository;
import com.ClinicaDeYmid.clients_service.module.repository.HealthProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeathProviderRecordServiceTest {

    @Mock
    private HealthProviderRepository healthProviderRepository;

    @Mock
    private HealthProviderMapper healthProviderMapper;

    @Mock
    private ContractRepository contractRepository;

    @InjectMocks
    private HeathProviderRecordService registrationService;

    private CreateHealthProviderDto createDto;
    private HealthProvider healthProvider;
    private Nit nit;

    @BeforeEach
    void setUp() {
        nit = new Nit("9001234567");
        createDto = new CreateHealthProviderDto(
                "Salud Total S.A.",
                nit,
                TypeProvider.EPS,
                "Calle 123",
                "1234567",
                2024,
                2026
        );

        healthProvider = new HealthProvider();
        healthProvider.setId(1L);
        healthProvider.setNit(nit);
        healthProvider.setSocialReason("Salud Total S.A.");
        healthProvider.setTypeProvider(TypeProvider.EPS);
        healthProvider.setContracts(Collections.emptyList());
    }

    @Test
    @DisplayName("Should create health provider successfully")
    void createHealthProvider_Success() {
        // Arrange
        when(healthProviderMapper.toEntity(createDto)).thenReturn(healthProvider);
        when(healthProviderRepository.existsByNit_Value(nit.getValue())).thenReturn(false);
        when(healthProviderRepository.save(healthProvider)).thenReturn(healthProvider);

        // Act
        HealthProvider result = registrationService.createHealthProvider(createDto);

        // Assert
        assertNotNull(result);
        assertEquals(healthProvider.getId(), result.getId());
        verify(healthProviderRepository).save(healthProvider);
    }

    @Test
    @DisplayName("Should throw exception when NIT already exists")
    void createHealthProvider_DuplicateNit() {
        // Arrange
        when(healthProviderMapper.toEntity(createDto)).thenReturn(healthProvider);
        when(healthProviderRepository.existsByNit_Value(nit.getValue())).thenReturn(true);

        // Act & Assert
        assertThrows(DuplicateHealthProviderNitException.class, () -> 
            registrationService.createHealthProvider(createDto)
        );
        verify(healthProviderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when Contract Number already exists")
    void createHealthProvider_DuplicateContract() {
        // Arrange
        Contract contract = new Contract();
        contract.setContractNumber("CN-123");
        healthProvider.setContracts(List.of(contract));

        when(healthProviderMapper.toEntity(createDto)).thenReturn(healthProvider);
        when(healthProviderRepository.existsByNit_Value(nit.getValue())).thenReturn(false);
        when(contractRepository.existsByContractNumber("CN-123")).thenReturn(true);

        // Act & Assert
        assertThrows(DuplicateContractNumberException.class, () -> 
            registrationService.createHealthProvider(createDto)
        );
        verify(healthProviderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception on data access error")
    void createHealthProvider_DataAccessException() {
        // Arrange
        when(healthProviderMapper.toEntity(createDto)).thenReturn(healthProvider);
        when(healthProviderRepository.existsByNit_Value(nit.getValue())).thenReturn(false);
        when(healthProviderRepository.save(healthProvider)).thenThrow(new DataIntegrityViolationException("DB Error"));

        // Act & Assert
        assertThrows(HealthProviderDataAccessException.class, () -> 
            registrationService.createHealthProvider(createDto)
        );
    }

    @Test
    @DisplayName("Should validate required fields manually")
    void createHealthProvider_ValidationException() {
        // Arrange
        HealthProvider invalidProvider = new HealthProvider(); // Missing NIT and Social Reason
        when(healthProviderMapper.toEntity(createDto)).thenReturn(invalidProvider);

        // Act & Assert
        assertThrows(HealthProviderValidationException.class, () -> 
            registrationService.createHealthProvider(createDto)
        );
    }
}

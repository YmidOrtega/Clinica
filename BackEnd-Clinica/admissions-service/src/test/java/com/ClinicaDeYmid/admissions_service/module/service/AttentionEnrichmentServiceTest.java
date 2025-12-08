package com.ClinicaDeYmid.admissions_service.module.service;

import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionResponseDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.ConfigurationServiceResponseDto;
import com.ClinicaDeYmid.admissions_service.module.dto.clients.GetHealthProviderDto;
import com.ClinicaDeYmid.admissions_service.module.dto.patient.GetPatientDto;
import com.ClinicaDeYmid.admissions_service.module.dto.suppliers.GetDoctorDto;
import com.ClinicaDeYmid.admissions_service.module.dto.user.GetUserDto;
import com.ClinicaDeYmid.admissions_service.module.entity.Attention;
import com.ClinicaDeYmid.admissions_service.module.entity.AttentionUserHistory;
import com.ClinicaDeYmid.admissions_service.module.entity.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.module.entity.HealthProviderInfo;
import com.ClinicaDeYmid.admissions_service.module.enums.UserActionType;
import com.ClinicaDeYmid.admissions_service.module.feignclient.DoctorClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.HealthProviderClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.PatientClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.UserClient;
import com.ClinicaDeYmid.admissions_service.module.mapper.AttentionMapper;
import com.ClinicaDeYmid.admissions_service.module.mapper.AuthorizationMapper;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttentionEnrichmentServiceTest {

    @Mock
    private PatientClient patientClient;
    @Mock
    private DoctorClient doctorClient;
    @Mock
    private HealthProviderClient healthProviderClient;
    @Mock
    private UserClient userClient;
    @Mock
    private AttentionMapper attentionMapper;
    @Mock
    private AuthorizationMapper authorizationMapper;

    @InjectMocks
    private AttentionEnrichmentService attentionEnrichmentService;

    private Attention attention;

    @BeforeEach
    void setUp() {
        attention = new Attention();
        attention.setId(1L);
        attention.setPatientId(1L);
        attention.setDoctorId(1L);
        
        ConfigurationService configService = new ConfigurationService();
        configService.setId(1L);
        attention.setConfigurationService(configService);

        HealthProviderInfo hpInfo = new HealthProviderInfo();
        hpInfo.setHealthProviderNit("123456789");
        hpInfo.setContractId(1L);
        attention.setHealthProviderNit(Collections.singletonList(hpInfo));

        AttentionUserHistory history = new AttentionUserHistory();
        history.setId(1L);
        history.setUserId(1L);
        history.setActionType(UserActionType.CREATED);
        history.setActionTimestamp(LocalDateTime.now());
        attention.setUserHistory(Collections.singletonList(history));
    }

    @Test
    void enrichAttentionResponseDto_Success() {
        // Arrange
        when(patientClient.getPatientByIdentificationNumber(anyString())).thenReturn(new GetPatientDto("123", "John", "Doe", null, null, null, null, null, null, null));
        when(doctorClient.getDoctorById(anyLong())).thenReturn(new GetDoctorDto("123", "Dr", "House", null));
        when(healthProviderClient.getHealthProviderByNitAndContract(anyString(), anyLong())).thenReturn(new GetHealthProviderDto("NIT", "SocialReason", "Type", null));
        when(userClient.getUserById(anyLong())).thenReturn(new GetUserDto("uuid", "user", "email", true, null));
        
        when(authorizationMapper.toResponseDtoList(anyList())).thenReturn(Collections.emptyList());
        when(attentionMapper.mapConfigurationServiceToResponseDto(any(ConfigurationService.class))).thenReturn(new ConfigurationServiceResponseDto(1L, "Service", "Care", "Loc", true));
        when(attentionMapper.toCompanionDto(any())).thenReturn(null);

        // Act
        AttentionResponseDto result = attentionEnrichmentService.enrichAttentionResponseDto(attention);

        // Assert
        assertNotNull(result);
        assertNotNull(result.patientDetails());
        assertNotNull(result.doctorDetails());
        assertFalse(result.healthProviderDetails().isEmpty());
        assertFalse(result.userHistory().isEmpty());
    }

    @Test
    void enrichAttentionResponseDto_ServiceUnavailable() {
        // Arrange
        when(patientClient.getPatientByIdentificationNumber(anyString())).thenReturn(new GetPatientDto("123", "John", "Doe", null, null, null, null, null, null, null));
        when(doctorClient.getDoctorById(anyLong())).thenThrow(FeignException.ServiceUnavailable.class);
        
        when(attentionMapper.mapConfigurationServiceToResponseDto(any(ConfigurationService.class))).thenReturn(new ConfigurationServiceResponseDto(1L, "Service", "Care", "Loc", true));

        // Act
        AttentionResponseDto result = attentionEnrichmentService.enrichAttentionResponseDto(attention);

        // Assert
        assertNotNull(result);
        assertNull(result.doctorDetails()); // Should be null due to exception
        assertNotNull(result.patientDetails()); // Should still be present
    }

    @Test
    void extractContractIdsFromHealthProviderInfo_Success() {
        // Arrange
        HealthProviderInfo info1 = new HealthProviderInfo();
        info1.setContractId(10L);
        HealthProviderInfo info2 = new HealthProviderInfo();
        info2.setContractId(20L);
        
        List<HealthProviderInfo> list = List.of(info1, info2);

        // Act
        List<Long> result = attentionEnrichmentService.extractContractIdsFromHealthProviderInfo(list);

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.contains(10L));
        assertTrue(result.contains(20L));
    }
}
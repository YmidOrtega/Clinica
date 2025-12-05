package com.ClinicaDeYmid.clients_service.module.controller;

import com.ClinicaDeYmid.clients_service.module.domain.Nit;
import com.ClinicaDeYmid.clients_service.module.dto.CreateHealthProviderDto;
import com.ClinicaDeYmid.clients_service.module.dto.HealthProviderListDto;
import com.ClinicaDeYmid.clients_service.module.dto.HealthProviderResponseDto;
import com.ClinicaDeYmid.clients_service.module.entity.HealthProvider;
import com.ClinicaDeYmid.clients_service.module.enums.TypeProvider;
import com.ClinicaDeYmid.clients_service.module.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HealthProviderControllerTest {

    private MockMvc mockMvc;

    @Mock
    private HeathProviderRecordService registrationService;
    @Mock
    private GetHealthProviderService getHealthProviderService;
    @Mock
    private UpdateHealthProviderService updateHealthProviderService;
    @Mock
    private StatusHealthProviderService statusHealthProviderService;
    @Mock
    private GetContractService getContractService;
    @Mock
    private StatusContractService statusContractService;
    @Mock
    private UpdateContractService updateContractService;
    @Mock
    private GetHealthProviderContractService getHealthProviderContractService;

    @InjectMocks
    private HealthProviderController healthProviderController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(healthProviderController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should create health provider successfully")
    void createHealthProvider_Success() throws Exception {
        Nit nit = new Nit("9001234567");
        CreateHealthProviderDto createDto = new CreateHealthProviderDto(
                "Salud Total", nit, TypeProvider.EPS, "Address", "1234567", 2024, 2025
        );
        HealthProvider provider = new HealthProvider();
        provider.setId(1L);
        provider.setNit(nit);

        when(registrationService.createHealthProvider(any(CreateHealthProviderDto.class))).thenReturn(provider);

        mockMvc.perform(post("/api/v1/billing-service/health-providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Should get all health providers")
    void getAllHealthProviders_Success() throws Exception {
        Nit nit = new Nit("9001234567");
        HealthProviderListDto dto = new HealthProviderListDto(1L, "Salud Total", nit, TypeProvider.EPS, "Address", "123", true);
        Page<HealthProviderListDto> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);

        when(getHealthProviderService.getAllHealthProviders(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/billing-service/health-providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].socialReason").value("Salud Total"));
    }

    @Test
    @DisplayName("Should get health provider by NIT")
    void getHealthProviderByNit_Success() throws Exception {
        Nit nit = new Nit("9001234567");
        HealthProviderResponseDto dto = new HealthProviderResponseDto(
                nit, "Salud Total", TypeProvider.EPS, Collections.emptyList(), com.ClinicaDeYmid.clients_service.module.enums.ContractStatus.ACTIVE
        );

        when(getHealthProviderService.getHealthProviderByNit("9001234567")).thenReturn(dto);

        mockMvc.perform(get("/api/v1/billing-service/health-providers/9001234567"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.socialReason").value("Salud Total"));
    }
}

package com.ClinicaDeYmid.clients_service.module.controller;

import com.ClinicaDeYmid.clients_service.infra.security.JwtAuthenticationFilter;
import com.ClinicaDeYmid.clients_service.infra.security.SecurityConfig;
import com.ClinicaDeYmid.clients_service.module.domain.Nit;
import com.ClinicaDeYmid.clients_service.module.dto.HealthProviderResponseDto;
import com.ClinicaDeYmid.clients_service.module.enums.ContractStatus;
import com.ClinicaDeYmid.clients_service.module.enums.TypeProvider;
import com.ClinicaDeYmid.clients_service.module.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthProviderController.class)
@Import(SecurityConfig.class)
class HealthProviderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HeathProviderRecordService registrationService;
    @MockBean
    private GetHealthProviderService getHealthProviderService;
    @MockBean
    private UpdateHealthProviderService updateHealthProviderService;
    @MockBean
    private StatusHealthProviderService statusHealthProviderService;
    @MockBean
    private GetContractService getContractService;
    @MockBean
    private StatusContractService statusContractService;
    @MockBean
    private UpdateContractService updateContractService;
    @MockBean
    private GetHealthProviderContractService getHealthProviderContractService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setup() throws Exception {
        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getHealthProviderByNit_Authorized_Success() throws Exception {
        Nit nit = new Nit("9001234567");
        HealthProviderResponseDto dto = new HealthProviderResponseDto(
                nit, "Salud Total", TypeProvider.EPS, Collections.emptyList(), ContractStatus.ACTIVE
        );

        when(getHealthProviderService.getHealthProviderByNit("9001234567")).thenReturn(dto);

        mockMvc.perform(get("/api/v1/billing-service/health-providers/9001234567"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.socialReason").value("Salud Total"));
    }

    @Test
    void getHealthProviderByNit_Unauthorized_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/billing-service/health-providers/9001234567"))
                .andExpect(status().isForbidden());
    }
}

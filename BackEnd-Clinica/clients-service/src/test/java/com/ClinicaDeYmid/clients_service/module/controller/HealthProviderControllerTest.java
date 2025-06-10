package com.ClinicaDeYmid.clients_service.module.controller;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.ClinicaDeYmid.clients_service.module.dto.HealthProviderListDto;
import com.ClinicaDeYmid.clients_service.module.service.GetHealthProviderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;

import java.util.Collections;

class HealthProviderControllerTest {

    @Mock
    private GetHealthProviderService getHealthProviderService;

    @Mock
    private PagedResourcesAssembler<HealthProviderListDto> assembler;

    @InjectMocks
    private HealthProviderController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void searchAllHealthProviders_returnsPagedModel() {
        PageRequest pageable = PageRequest.of(0, 10);
        HealthProviderListDto dto = mock(HealthProviderListDto.class);
        Page<HealthProviderListDto> page = new PageImpl<>(Collections.singletonList(dto), pageable, 1);

        when(getHealthProviderService.getAllHealthProviders(pageable)).thenReturn(page);

        PagedModel<EntityModel<HealthProviderListDto>> pagedModel = PagedModel.empty();
        when(assembler.toModel(page)).thenReturn(pagedModel);

        ResponseEntity<PagedModel<EntityModel<HealthProviderListDto>>> response =
                controller.searchAllHealthProviders(pageable, assembler);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo(pagedModel);

        verify(getHealthProviderService).getAllHealthProviders(pageable);
        verify(assembler).toModel(page);
    }
}



package com.ClinicaDeYmid.admissions_service.module.controller;

import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionRequestDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionResponseDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionSearchRequest;
import com.ClinicaDeYmid.admissions_service.module.service.AttentionGetService;
import com.ClinicaDeYmid.admissions_service.module.service.AttentionRecordService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/attentions")
@RequiredArgsConstructor
public class AttentionController {

    private final AttentionGetService attentionGetService;
    private final AttentionRecordService attentionRecordService;

    @PostMapping
    @CircuitBreaker(name = "admissions-service", fallbackMethod = "createAttentionFallback")
    public ResponseEntity<AttentionResponseDto> createAttention(
            @Valid @RequestBody AttentionRequestDto requestDto,
            UriComponentsBuilder uriBuilder) {

        log.info("Creating new attention for patient ID: {}", requestDto.patientId());

        AttentionResponseDto responseDto = attentionRecordService.createAttention(requestDto);

        URI uri = uriBuilder.path("/api/v1/attentions/{id}")
                .buildAndExpand(responseDto.id())
                .toUri();

        log.info("Attention created successfully with ID: {}", responseDto.id());
        return ResponseEntity.created(uri).body(responseDto);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AttentionResponseDto> getAttentionById(
            @PathVariable @NotNull @Positive(message = "Attention ID must be positive") Long id) {

        log.info("Retrieving attention with ID: {}", id);

        AttentionResponseDto responseDto = attentionGetService.getAttentionById(id);
        return ResponseEntity.ok(responseDto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AttentionResponseDto> updateAttention(
            @PathVariable @NotNull @Positive(message = "Attention ID must be positive") Long id,
            @Valid @RequestBody AttentionRequestDto requestDto) {

        log.info("Updating attention with ID: {}", id);

        AttentionResponseDto responseDto = attentionRecordService.updateAttention(id, requestDto);

        log.info("Attention updated successfully with ID: {}", id);
        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<AttentionResponseDto>> getAttentionsByPatientId(
            @PathVariable @NotNull @Positive(message = "Patient ID must be positive") Long patientId) {

        log.info("Retrieving attentions for patient ID: {}", patientId);

        List<AttentionResponseDto> attentions = attentionGetService.getAttentionsByPatientId(patientId);
        return ResponseEntity.ok(attentions);
    }

    @GetMapping("/patient/{patientId}/active")
    public ResponseEntity<AttentionResponseDto> getActiveAttentionByPatientId(
            @PathVariable @NotNull @Positive(message = "Patient ID must be positive") Long patientId) {

        log.info("Retrieving active attention for patient ID: {}", patientId);

        AttentionResponseDto responseDto = attentionGetService.getActiveAttentionByPatientId(patientId);
        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<List<AttentionResponseDto>> getAttentionsByDoctorId(
            @PathVariable @NotNull @Positive(message = "Doctor ID must be positive") Long doctorId) {

        log.info("Retrieving attentions for doctor ID: {}", doctorId);

        List<AttentionResponseDto> attentions = attentionGetService.getAttentionsByDoctorId(doctorId);
        return ResponseEntity.ok(attentions);
    }

    @GetMapping("/health-provider/{healthProviderNit}")
    public ResponseEntity<List<AttentionResponseDto>> getAttentionsByHealthProviderId(
            @PathVariable @NotNull String healthProviderNit) {

        log.info("Retrieving attentions for health provider NIT: {}", healthProviderNit);

        List<AttentionResponseDto> attentions = attentionGetService.getAttentionsByHealthProviderId(healthProviderNit);
        return ResponseEntity.ok(attentions);
    }

    @GetMapping("/configuration-service/{configServiceId}")
    public ResponseEntity<List<AttentionResponseDto>> getAttentionsByConfigurationServiceId(
            @PathVariable @NotNull @Positive(message = "Configuration service ID must be positive") Long configServiceId) {

        log.info("Retrieving attentions for configuration service ID: {}", configServiceId);

        List<AttentionResponseDto> attentions = attentionGetService.getAttentionsByConfigurationServiceId(configServiceId);
        return ResponseEntity.ok(attentions);
    }

    @GetMapping("/search")
    public ResponseEntity<PagedModel<EntityModel<AttentionResponseDto>>> searchAttentions(
            @Valid AttentionSearchRequest searchRequest,
            PagedResourcesAssembler<AttentionResponseDto> assembler) {

        log.info("Performing attention search with criteria: {}", searchRequest);

        Page<AttentionResponseDto> attentionsPage = attentionGetService.searchAttentions(searchRequest);

        return ResponseEntity.ok(assembler.toModel(attentionsPage));
    }

    @GetMapping("/{id}/can-update")
    public ResponseEntity<Boolean> canUpdateAttention(
            @PathVariable @NotNull @Positive(message = "Attention ID must be positive") Long id) {

        log.info("Checking if attention with ID: {} can be updated", id);

        boolean canUpdate = attentionRecordService.canUpdateAttention(id);
        return ResponseEntity.ok(canUpdate);
    }

    @GetMapping("/{id}/invoice-status")
    public ResponseEntity<String> getInvoiceStatus(
            @PathVariable @NotNull @Positive(message = "Attention ID must be positive") Long id) {

        log.info("Retrieving invoice status for attention with ID: {}", id);

        String invoiceStatus = attentionRecordService.getInvoiceStatus(id);
        return ResponseEntity.ok(invoiceStatus);
    }

    private ResponseEntity<AttentionResponseDto> createAttentionFallback(
            AttentionRequestDto requestDto, UriComponentsBuilder uriBuilder, Throwable throwable) {

        log.error("Error creating attention: {}", throwable.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
}
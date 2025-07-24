package com.ClinicaDeYmid.admissions_service.module.controller;

import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionRequestDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionResponseDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionSearchRequest;
import com.ClinicaDeYmid.admissions_service.module.dto.clients.HealthProviderWithAttentionsResponse;
import com.ClinicaDeYmid.admissions_service.module.dto.patient.PatientWithAttentionsResponse;
import com.ClinicaDeYmid.admissions_service.module.dto.suppliers.DoctorWithAttentionsResponse;
import com.ClinicaDeYmid.admissions_service.module.service.AttentionGetService;
import com.ClinicaDeYmid.admissions_service.module.service.AttentionRecordService;
import com.ClinicaDeYmid.admissions_service.module.service.PdfGeneratorService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
@Tag(name = "Attention Management", description = "Operations related to patient attentions")
public class AttentionController {

    private final AttentionGetService attentionGetService;
    private final AttentionRecordService attentionRecordService;
    private final PdfGeneratorService pdfGeneratorService;

    @PostMapping
    @CircuitBreaker(name = "admissions-service", fallbackMethod = "createAttentionFallback")
    @Operation (summary = "Create a new attention", description = "Creates a new attention record for a patient.")
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
    @Operation(summary = "Retrieve attention by ID", description = "Fetches the details of an attention record by its ID.")
    public ResponseEntity<AttentionResponseDto> getAttentionById(
            @PathVariable @NotNull @Positive(message = "Attention ID must be positive") Long id) {

        log.info("Retrieving attention with ID: {}", id);

        AttentionResponseDto responseDto = attentionGetService.getAttentionById(id);
        return ResponseEntity.ok(responseDto);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update attention by ID", description = "Updates the details of an existing attention record.")
    public ResponseEntity<AttentionResponseDto> updateAttention(
            @PathVariable @NotNull @Positive(message = "Attention ID must be positive") Long id,
            @Valid @RequestBody AttentionRequestDto requestDto) {

        log.info("Updating attention with ID: {}", id);

        AttentionResponseDto responseDto = attentionRecordService.updateAttention(id, requestDto);

        log.info("Attention updated successfully with ID: {}", id);
        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/patient/{patientId}")
    @Operation(summary = "Retrieve attentions by patient ID", description = "Fetches all attentions associated with a specific patient.")
    public ResponseEntity<List<PatientWithAttentionsResponse>> getAttentionsByPatientId(
            @PathVariable @NotNull @Positive(message = "Patient ID must be positive") Long patientId) {

        log.info("Retrieving attentions for patient ID: {}", patientId);

        List<PatientWithAttentionsResponse> attentions = attentionGetService.getAttentionsByPatientId(patientId);
        return ResponseEntity.ok(attentions);
    }

    @GetMapping("/patient/{patientId}/active")
    @Operation(summary = "Retrieve active attention by patient ID", description = "Fetches the active attention record for a specific patient.")
    public ResponseEntity<List<PatientWithAttentionsResponse>> getActiveAttentionByPatientId(
            @PathVariable @NotNull @Positive(message = "Patient ID must be positive") Long patientId) {

        log.info("Retrieving active attention for patient ID: {}", patientId);

        List<PatientWithAttentionsResponse> responseDto = attentionGetService.getActiveAttentionByPatientId(patientId);
        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/doctor/{doctorId}")
    @Operation(summary = "Retrieve attentions by doctor ID", description = "Fetches all attentions associated with a specific doctor.")
    public ResponseEntity<List<DoctorWithAttentionsResponse>> getAttentionsByDoctorId(
            @PathVariable @NotNull @Positive(message = "Doctor ID must be positive") Long doctorId) {

        log.info("Retrieving attentions for doctor ID: {}", doctorId);

        List<DoctorWithAttentionsResponse> attentions = attentionGetService.getAttentionsByDoctorId(doctorId);
        return ResponseEntity.ok(attentions);
    }

    @GetMapping("/health-provider/{healthProviderNit}")
    @Operation(summary = "Retrieve attentions by health provider NIT", description = "Fetches all attentions associated with a specific health provider.")
    public ResponseEntity<List<HealthProviderWithAttentionsResponse>> getAttentionsByHealthProviderId(
            @PathVariable @NotNull String healthProviderNit) {

        log.info("Retrieving attentions for health provider NIT: {}", healthProviderNit);

        List<HealthProviderWithAttentionsResponse> attentions = attentionGetService.getGroupedAttentionsByHealthProvider(healthProviderNit);
        return ResponseEntity.ok(attentions);
    }

    @GetMapping("/configuration-service/{configServiceId}")
    @Operation(summary = "Retrieve attentions by configuration service ID", description = "Fetches all attentions associated with a specific configuration service.")
    public ResponseEntity<List<AttentionResponseDto>> getAttentionsByConfigurationServiceId(
            @PathVariable @NotNull @Positive(message = "Configuration service ID must be positive") Long configServiceId) {

        log.info("Retrieving attentions for configuration service ID: {}", configServiceId);

        List<AttentionResponseDto> attentions = attentionGetService.getAttentionsByConfigurationServiceId(configServiceId);
        return ResponseEntity.ok(attentions);
    }

    @GetMapping("/search")
    @Operation(summary = "Search attentions", description = "Searches for attentions based on various criteria.")
    public ResponseEntity<PagedModel<EntityModel<AttentionResponseDto>>> searchAttentions(
            @Valid AttentionSearchRequest searchRequest,
            PagedResourcesAssembler<AttentionResponseDto> assembler) {

        log.info("Performing attention search with criteria: {}", searchRequest);

        Page<AttentionResponseDto> attentionsPage = attentionGetService.searchAttentions(searchRequest);

        return ResponseEntity.ok(assembler.toModel(attentionsPage));
    }

    @GetMapping("/{id}/can-update")
    @Operation(summary = "Check if attention can be updated", description = "Checks if an attention record can be updated based on its ID.")
    public ResponseEntity<Boolean> canUpdateAttention(
            @PathVariable @NotNull @Positive(message = "Attention ID must be positive") Long id) {

        log.info("Checking if attention with ID: {} can be updated", id);

        boolean canUpdate = attentionRecordService.canUpdateAttention(id);
        return ResponseEntity.ok(canUpdate);
    }

    @GetMapping("/{id}/invoice-status")
    @Operation(summary = "Get invoice status for attention", description = "Retrieves the invoice status for a specific attention record by its ID.")
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

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Generate PDF for attention", description = "Generates a PDF document with the details of an attention record.")
    public ResponseEntity<byte[]> generateAttentionPdf(
            @PathVariable @NotNull @Positive(message = "Attention ID must be positive") Long id) {

        log.info("Generating PDF for attention with ID: {}", id);

        // Obtener la atención
        AttentionResponseDto attention = attentionGetService.getAttentionById(id);

        // Generar el PDF
        byte[] pdfContent = pdfGeneratorService.generateAttentionPdf(attention);

        // Configurar headers para la descarga
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "atencion-" + id + ".pdf");
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

        log.info("PDF generated successfully for attention with ID: {}", id);
        return new ResponseEntity<>(pdfContent, headers, HttpStatus.OK);
    }
}
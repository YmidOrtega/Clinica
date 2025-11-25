package com.ClinicaDeYmid.admissions_service.module.controller;

import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionRequestDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionResponseDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionSearchRequest;
import com.ClinicaDeYmid.admissions_service.module.dto.clients.HealthProviderWithAttentionsResponse;
import com.ClinicaDeYmid.admissions_service.module.dto.patient.PatientWithAttentionsResponse;
import com.ClinicaDeYmid.admissions_service.module.dto.suppliers.DoctorWithAttentionsResponse;
import com.ClinicaDeYmid.admissions_service.module.service.AttentionGetService;
import com.ClinicaDeYmid.admissions_service.module.service.AttentionRecordService;
import com.ClinicaDeYmid.admissions_service.module.service.AttentionStatusService;
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
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final AttentionStatusService attentionStatusService;
    private final PdfGeneratorService pdfGeneratorService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'RECEPTIONIST')")
    @CircuitBreaker(name = "admissions-service", fallbackMethod = "createAttentionFallback")
    @Operation(summary = "Create a new attention", description = "Creates a new attention record for a patient. Requires SUPER_ADMIN, ADMIN, or RECEPTIONIST role.")
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
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    @CircuitBreaker(name = "admissions-service", fallbackMethod = "getAttentionByIdFallback")
    @Operation(summary = "Get attention by ID", description = "Retrieves an attention by its unique identifier. Accessible by SUPER_ADMIN, ADMIN, RECEPTIONIST, and DOCTOR roles.")
    public ResponseEntity<AttentionResponseDto> getAttentionById(
            @PathVariable @NotNull @Positive Long id) {

        log.info("Retrieving attention with ID: {}", id);

        AttentionResponseDto responseDto = attentionGetService.getAttentionById(id);
        return ResponseEntity.ok(responseDto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'RECEPTIONIST')")
    @CircuitBreaker(name = "admissions-service", fallbackMethod = "updateAttentionFallback")
    @Operation(summary = "Update an existing attention", description = "Updates an existing attention record. Requires SUPER_ADMIN, ADMIN, or RECEPTIONIST role.")
    public ResponseEntity<AttentionResponseDto> updateAttention(
            @PathVariable @NotNull @Positive(message = "Attention ID must be positive") Long id,
            @Valid @RequestBody AttentionRequestDto requestDto) {

        log.info("Updating attention with ID: {}", id);

        AttentionResponseDto responseDto = attentionRecordService.updateAttention(id, requestDto);

        log.info("Attention updated successfully with ID: {}", id);
        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    @CircuitBreaker(name = "admissions-service", fallbackMethod = "getAttentionsByPatientIdFallback")
    @Operation(summary = "Get attentions by patient ID", description = "Retrieves all attentions for a specific patient. Accessible by SUPER_ADMIN, ADMIN, RECEPTIONIST, and DOCTOR roles.")
    public ResponseEntity<List<PatientWithAttentionsResponse>> getAttentionsByPatientId(
            @PathVariable @NotNull @Positive Long patientId) {

        log.info("Fetching attentions for patient ID: {}", patientId);

        List<PatientWithAttentionsResponse> response = attentionGetService.getAttentionsByPatientId(patientId);

        return ResponseEntity.ok(response);
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
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DOCTOR')")
    @CircuitBreaker(name = "admissions-service", fallbackMethod = "getAttentionsByDoctorIdFallback")
    @Operation(summary = "Get attentions by doctor ID", description = "Retrieves all attentions assigned to a specific doctor. Accessible by SUPER_ADMIN, ADMIN, and DOCTOR roles.")
    public ResponseEntity<List<DoctorWithAttentionsResponse>> getAttentionsByDoctorId(
            @PathVariable @NotNull @Positive Long doctorId) {

        log.info("Fetching attentions for doctor ID: {}", doctorId);

        List<DoctorWithAttentionsResponse> response = attentionGetService.getAttentionsByDoctorId(doctorId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/health-provider/{nit}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'RECEPTIONIST')")
    @CircuitBreaker(name = "admissions-service", fallbackMethod = "getAttentionsByHealthProviderNitFallback")
    @Operation(summary = "Get attentions by health provider NIT", description = "Retrieves all attentions for a specific health provider. Accessible by SUPER_ADMIN, ADMIN, and RECEPTIONIST roles.")
    public ResponseEntity<List<HealthProviderWithAttentionsResponse>> getAttentionsByHealthProviderNit(
            @PathVariable @NotNull String nit) {

        log.info("Fetching attentions for health provider NIT: {}", nit);

        List<HealthProviderWithAttentionsResponse> response = attentionGetService.getAttentionsByHealthProviderNit(nit);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/configuration-service/{configServiceId}")
    @Operation(summary = "Retrieve attentions by configuration service ID", description = "Fetches all attentions associated with a specific configuration service.")
    public ResponseEntity<List<AttentionResponseDto>> getAttentionsByConfigurationServiceId(
            @PathVariable @NotNull @Positive(message = "Configuration service ID must be positive") Long configServiceId) {

        log.info("Retrieving attentions for configuration service ID: {}", configServiceId);

        List<AttentionResponseDto> attentions = attentionGetService.getAttentionsByConfigurationServiceId(configServiceId);
        return ResponseEntity.ok(attentions);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'RECEPTIONIST')")
    @CircuitBreaker(name = "admissions-service", fallbackMethod = "searchAttentionsFallback")
    @Operation(summary = "Search attentions", description = "Search and filter attentions with pagination. Accessible by SUPER_ADMIN, ADMIN, and RECEPTIONIST roles.")
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

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    @Operation(summary = "Download attention PDF", description = "Downloads a PDF report of the attention. Accessible by SUPER_ADMIN, ADMIN, RECEPTIONIST, and DOCTOR roles.")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable @NotNull @Positive Long id) {

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

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Activate an attention", description = "Activates an attention by ID. Only SUPER_ADMIN and ADMIN roles can perform this operation.")
    public ResponseEntity<Void> activateAttention(@PathVariable @NotNull @Positive Long id) {
        log.info("Activating attention with ID: {}", id);
        attentionStatusService.activateAttention(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Deactivate an attention", description = "Deactivates an attention by ID. Only SUPER_ADMIN and ADMIN roles can perform this operation.")
    public ResponseEntity<Void> deactivateAttention(@PathVariable @NotNull @Positive Long id) {
        log.info("Deactivating attention with ID: {}", id);
        attentionStatusService.deactivateAttention(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Soft delete an attention", description = "Performs a soft delete on an attention. Only SUPER_ADMIN role can perform this operation. Invoiced attentions cannot be deleted.")
    public ResponseEntity<Void> softDeleteAttention(
            @PathVariable @NotNull @Positive Long id,
            @RequestParam(required = false) String reason) {
        log.info("Soft deleting attention with ID: {} for reason: {}", id, reason);
        attentionStatusService.softDeleteAttention(id, reason);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Restore a soft-deleted attention", description = "Restores a previously soft-deleted attention. Only SUPER_ADMIN role can perform this operation.")
    public ResponseEntity<AttentionResponseDto> restoreAttention(@PathVariable @NotNull @Positive Long id) {
        log.info("Restoring attention with ID: {}", id);
        attentionStatusService.restoreAttention(id);
        AttentionResponseDto response = attentionGetService.getAttentionById(id);
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<AttentionResponseDto> createAttentionFallback(
            AttentionRequestDto requestDto, UriComponentsBuilder uriBuilder, Throwable throwable) {

        log.error("Error creating attention: {}", throwable.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    private ResponseEntity<List<PatientWithAttentionsResponse>> getAttentionsByPatientIdFallback(Long patientId, Throwable ex) {
        log.error("Circuit breaker activated for getAttentionsByPatientId. PatientId: {}, Error: {}", patientId, ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    private ResponseEntity<List<DoctorWithAttentionsResponse>> getAttentionsByDoctorIdFallback(Long doctorId, Throwable ex) {
        log.error("Circuit breaker activated for getAttentionsByDoctorId. DoctorId: {}, Error: {}", doctorId, ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    private ResponseEntity<List<HealthProviderWithAttentionsResponse>> getAttentionsByHealthProviderNitFallback(String nit, Throwable ex) {
        log.error("Circuit breaker activated for getAttentionsByHealthProviderNit. NIT: {}, Error: {}", nit, ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
}
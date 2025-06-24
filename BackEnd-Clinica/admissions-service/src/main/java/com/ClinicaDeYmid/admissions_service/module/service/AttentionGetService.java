package com.ClinicaDeYmid.admissions_service.module.service;

import com.ClinicaDeYmid.admissions_service.module.dto.AttentionSearchRequest;
import com.ClinicaDeYmid.admissions_service.module.dto.AttentionSummary;
import com.ClinicaDeYmid.admissions_service.module.dto.PagedAttentionResponse;
import com.ClinicaDeYmid.admissions_service.module.dto.clients.GetHealthProviderDto;
import com.ClinicaDeYmid.admissions_service.module.dto.patient.GetPatientDto;
import com.ClinicaDeYmid.admissions_service.module.dto.suppliers.GetDoctorDto;
import com.ClinicaDeYmid.admissions_service.module.entity.Attention;
import com.ClinicaDeYmid.admissions_service.infra.exception.EntityNotFoundException;
import com.ClinicaDeYmid.admissions_service.infra.exception.ValidationException;
import com.ClinicaDeYmid.admissions_service.module.feignclient.DoctorClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.HealthProviderClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.PatientClient;
import com.ClinicaDeYmid.admissions_service.module.mapper.AttentionMapper;
import com.ClinicaDeYmid.admissions_service.module.repository.AttentionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttentionGetService {

    private final AttentionRepository attentionRepository;
    private final AttentionMapper attentionMapper;
    private final PatientClient patientClient;
    private final DoctorClient doctorClient;
    private final HealthProviderClient healthProviderClient;

    @Value("${app.external-service.timeout:5000}")
    private long externalServiceTimeoutMs;

    public AttentionSummary getAttentionById(Long attentionId) {
        log.info("Fetching attention with ID: {}", attentionId);
        Instant start = Instant.now();

        Attention attention = attentionRepository.findById(attentionId)
                .orElseThrow(() -> new EntityNotFoundException("Attention not found with ID: " + attentionId));

        AttentionSummary result = getAttentionSummaryWithDetails(attention);

        logPerformance("getAttentionById", start, 1);
        return result;
    }


    public List<AttentionSummary> getAttentionsByPatientId(Long patientId) {
        log.info("Fetching all attentions for patient ID: {}", patientId);
        Instant start = Instant.now();

        List<Attention> attentions = attentionRepository.findByPatientIdOrderByAdmissionDateTimeDesc(patientId);
        List<AttentionSummary> result = attentions.stream()
                .map(this::getAttentionSummaryWithDetails)
                .collect(Collectors.toList());

        logPerformance("getAttentionsByPatientId", start, attentions.size());
        return result;
    }

    public Optional<AttentionSummary> getActiveAttentionByPatientId(Long patientId) {
        log.info("Fetching active attention for patient ID: {}", patientId);
        Instant start = Instant.now();

        Optional<AttentionSummary> result = attentionRepository.findByPatientIdAndDischargeeDateTimeIsNull(patientId)
                .map(this::getAttentionSummaryWithDetails);

        logPerformance("getActiveAttentionByPatientId", start, result.isPresent() ? 1 : 0);
        return result;
    }

    public List<AttentionSummary> getAttentionsByDoctorId(Long doctorId) {
        log.info("Fetching all attentions for doctor ID: {}", doctorId);
        Instant start = Instant.now();

        List<Attention> attentions = attentionRepository.findByDoctorIdOrderByAdmissionDateTimeDesc(doctorId);
        List<AttentionSummary> result = attentions.stream()
                .map(this::getAttentionSummaryWithDetails)
                .collect(Collectors.toList());

        logPerformance("getAttentionsByDoctorId", start, attentions.size());
        return result;
    }

    public PagedAttentionResponse searchAttentions(AttentionSearchRequest searchRequest) {
        log.info("Searching attentions with criteria: {}", searchRequest);
        Instant start = Instant.now();

        validateSearchRequest(searchRequest);

        Pageable pageable = buildPageable(searchRequest);

        Page<Attention> attentionPage = attentionRepository.findAll(pageable);

        List<AttentionSummary> summaries = attentionPage.getContent().stream()
                .map(this::getAttentionSummaryWithDetails)
                .collect(Collectors.toList());

        logPerformance("searchAttentions", start, summaries.size());

        return new PagedAttentionResponse(
                summaries,
                attentionPage.getNumber(),
                attentionPage.getSize(),
                attentionPage.getTotalElements(),
                attentionPage.getTotalPages(),
                attentionPage.isFirst(),
                attentionPage.isLast(),
                attentionPage.hasNext(),
                attentionPage.hasPrevious()
        );
    }

    public List<AttentionSummary> getAttentionsByHealthProviderId(String healthProviderNit) {
        log.info("Fetching attentions for health provider ID: {}", healthProviderNit);
        Instant start = Instant.now();

        List<Attention> attentions = attentionRepository
                .findByHealthProviderNitContainingOrderByAdmissionDateTimeDesc(healthProviderNit);

        List<AttentionSummary> result = attentions.stream()
                .map(this::getAttentionSummaryWithDetails)
                .collect(Collectors.toList());

        logPerformance("getAttentionsByHealthProviderId", start, attentions.size());
        return result;
    }

    public List<AttentionSummary> getAttentionsByConfigurationServiceId(Long configServiceId) {
        log.info("Fetching attentions for configuration service ID: {}", configServiceId);
        Instant start = Instant.now();

        List<Attention> attentions = attentionRepository
                .findByConfigurationServiceIdOrderByAdmissionDateTimeDesc(configServiceId);

        List<AttentionSummary> result = attentions.stream()
                .map(this::getAttentionSummaryWithDetails)
                .collect(Collectors.toList());

        logPerformance("getAttentionsByConfigurationServiceId", start, attentions.size());
        return result;
    }

    public long countActiveAttentions() {
        log.debug("Counting active attentions");
        return attentionRepository.countByDischargeeDateTimeIsNull();
    }

    public long countAttentionsByPatientId(Long patientId) {
        log.debug("Counting attentions for patient ID: {}", patientId);
        return attentionRepository.countByPatientId(patientId);
    }

    public boolean hasActiveAttention(Long patientId) {
        log.debug("Checking if patient ID: {} has active attention", patientId);
        return attentionRepository.existsByPatientIdAndDischargeeDateTimeIsNull(patientId);
    }

    public boolean existsAttentionById(Long attentionId) {
        log.debug("Checking if attention exists with ID: {}", attentionId);
        return attentionRepository.existsById(attentionId);
    }

    /**
     * Construye un AttentionSummary completo con todos los datos externos.
     */
    private AttentionSummary getAttentionSummaryWithDetails(Attention attention) {
        log.debug("Building detailed summary for attention ID: {}", attention.getId());

        // Obtener datos básicos del mapper
        AttentionSummary basicSummary = attentionMapper.toSummary(attention);

        CompletableFuture<GetPatientDto> patientFuture = CompletableFuture
                .supplyAsync(() -> fetchPatientSafely(attention.getPatientId()));

        CompletableFuture<GetDoctorDto> doctorFuture = CompletableFuture
                .supplyAsync(() -> fetchDoctorSafely(attention.getDoctorId()));

        CompletableFuture<List<GetHealthProviderDto>> healthProvidersFuture = CompletableFuture
                .supplyAsync(() -> fetchHealthProvidersSafely(attention.getHealthProviderNit()));

        try {

            GetPatientDto patientDto = patientFuture.get(externalServiceTimeoutMs, TimeUnit.MILLISECONDS);
            GetDoctorDto doctorDto = doctorFuture.get(externalServiceTimeoutMs, TimeUnit.MILLISECONDS);
            List<GetHealthProviderDto> healthProviders = healthProvidersFuture.get(externalServiceTimeoutMs, TimeUnit.MILLISECONDS);

            // Construir el summary completo
            return new AttentionSummary(
                    basicSummary.id(),
                    patientDto,
                    doctorDto,
                    healthProviders,
                    basicSummary.status(),
                    basicSummary.admissionDateTime(),
                    basicSummary.dischargeDateTime(),
                    basicSummary.triageLevel(),
                    basicSummary.cause(),
                    basicSummary.mainDiagnosisCode(),
                    basicSummary.invoiced(),
                    basicSummary.configurationService(),
                    collectWarnings(patientDto, doctorDto, healthProviders)
            );

        } catch (Exception e) {
            log.error("Error fetching external data for attention ID: {}", attention.getId(), e);

            // Devolver datos básicos con warning
            return new AttentionSummary(
                    basicSummary.id(),
                    null, // patientDto
                    null, // doctorDto
                    List.of(), // healthProviders
                    basicSummary.status(),
                    basicSummary.admissionDateTime(),
                    basicSummary.dischargeDateTime(),
                    basicSummary.triageLevel(),
                    basicSummary.cause(),
                    basicSummary.mainDiagnosisCode(),
                    basicSummary.invoiced(),
                    basicSummary.configurationService(),
                    List.of("Error fetching external data: " + e.getMessage())
            );
        }
    }

    /**
     * Obtiene datos del paciente de forma segura
     */
    private GetPatientDto fetchPatientSafely(Long patientId) {
        if (patientId == null) {
            return null;
        }

        try {
            log.debug("Fetching patient data for ID: {}", patientId);
            return patientClient.getPatientByIdentificationNumber(patientId.toString());
        } catch (Exception e) {
            log.warn("Patient with ID {} not found: {}", patientId, e.getMessage());
            return null;
        }
    }

    /**
     * Obtiene datos del doctor de forma segura
     */
    private GetDoctorDto fetchDoctorSafely(Long doctorId) {
        if (doctorId == null) {
            return null;
        }

        try {
            log.debug("Fetching doctor data for ID: {}", doctorId);
            return doctorClient.getDoctorById(doctorId);
        } catch (Exception e) {
            log.warn("Doctor with ID {} not found: {}", doctorId, e.getMessage());
            return null;
        }
    }

    /**
     * Obtiene datos de los proveedores de salud de forma segura
     */
    private List<GetHealthProviderDto> fetchHealthProvidersSafely(List<String> healthProviderNits) {
        if (healthProviderNits == null || healthProviderNits.isEmpty()) {
            return List.of();
        }

        List<GetHealthProviderDto> providers = new ArrayList<>();

        for (String nit : healthProviderNits) {
            try {
                log.debug("Fetching health provider data for NIT: {}", nit);
                GetHealthProviderDto provider = healthProviderClient.getHealthProviderByNit(nit);
                if (provider != null) {
                    providers.add(provider);
                }
            } catch (Exception e) {
                log.warn("Health provider with NIT {} not found: {}", nit, e.getMessage());
            }
        }

        return providers;
    }

    /**
     * Recolecta warnings basados en datos faltantes
     */
    private List<String> collectWarnings(GetPatientDto patient, GetDoctorDto doctor, List<GetHealthProviderDto> providers) {
        List<String> warnings = new ArrayList<>();

        if (patient == null) {
            warnings.add("Patient data not available");
        }
        if (doctor == null) {
            warnings.add("Doctor data not available");
        }
        if (providers.isEmpty()) {
            warnings.add("Health provider data not available");
        }

        return warnings;
    }

    /**
     * Registra métricas de performance
     */
    private void logPerformance(String operation, Instant start, int recordCount) {
        Duration duration = Duration.between(start, Instant.now());
        log.info("Operation: {} completed in {}ms for {} records",
                operation, duration.toMillis(), recordCount);
    }


    private Pageable buildPageable(AttentionSearchRequest searchRequest) {
        int page = searchRequest.page() != null ? searchRequest.page() : 0;
        int size = searchRequest.size() != null ? searchRequest.size() : 10;

        String sortBy = searchRequest.sortBy() != null ? searchRequest.sortBy() : "admissionDateTime";
        String sortDirection = searchRequest.sortDirection() != null ? searchRequest.sortDirection() : "desc";

        Sort sort = Sort.by(
                sortDirection.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC,
                sortBy
        );

        return PageRequest.of(page, size, sort);
    }

    private void validateSearchRequest(AttentionSearchRequest searchRequest) {
        if (searchRequest.admissionDateFrom() != null && searchRequest.admissionDateTo() != null) {
            if (searchRequest.admissionDateFrom().isAfter(searchRequest.admissionDateTo())) {
                throw new ValidationException("Admission date from cannot be after admission date to");
            }
        }

        if (searchRequest.dischargeDateFrom() != null && searchRequest.dischargeDateTo() != null) {
            if (searchRequest.dischargeDateFrom().isAfter(searchRequest.dischargeDateTo())) {
                throw new ValidationException("Discharge date from cannot be after discharge date to");
            }
        }

        if (searchRequest.page() != null && searchRequest.page() < 0) {
            throw new ValidationException("Page number cannot be negative");
        }

        if (searchRequest.size() != null && (searchRequest.size() < 1 || searchRequest.size() > 100)) {
            throw new ValidationException("Page size must be between 1 and 100");
        }
    }

}
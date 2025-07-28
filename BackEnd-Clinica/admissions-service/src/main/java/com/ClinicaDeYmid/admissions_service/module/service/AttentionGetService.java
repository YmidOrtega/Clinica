package com.ClinicaDeYmid.admissions_service.module.service;

import com.ClinicaDeYmid.admissions_service.infra.exception.EntityNotFoundException;
import com.ClinicaDeYmid.admissions_service.infra.exception.ValidationException;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionResponseDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionSearchRequest;
import com.ClinicaDeYmid.admissions_service.module.dto.clients.GetHealthProviderDto;
import com.ClinicaDeYmid.admissions_service.module.dto.clients.HealthProviderWithAttentionsResponse;
import com.ClinicaDeYmid.admissions_service.module.dto.patient.GetPatientDto;
import com.ClinicaDeYmid.admissions_service.module.dto.patient.PatientWithAttentionsResponse;
import com.ClinicaDeYmid.admissions_service.module.dto.suppliers.DoctorWithAttentionsResponse;
import com.ClinicaDeYmid.admissions_service.module.dto.suppliers.GetDoctorDto;
import com.ClinicaDeYmid.admissions_service.module.entity.Attention;
import com.ClinicaDeYmid.admissions_service.module.entity.HealthProviderInfo;
import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;
import com.ClinicaDeYmid.admissions_service.module.feignclient.DoctorClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.HealthProviderClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.PatientClient;
import com.ClinicaDeYmid.admissions_service.module.mapper.AttentionMapper;
import com.ClinicaDeYmid.admissions_service.module.repository.AttentionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.ClinicaDeYmid.admissions_service.module.repository.AttentionSpecifications.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttentionGetService {

    private final AttentionRepository attentionRepository;
    private final AttentionEnrichmentService attentionEnrichmentService;
    private final AttentionMapper attentionMapper;
    private final PatientClient patientClient;
    private final DoctorClient doctorClient;
    private final HealthProviderClient healthProviderClient;

    @Transactional(readOnly = true)
    public AttentionResponseDto getAttentionById(Long id) {
        log.info("Fetching attention with ID: {}", id);
        Attention attention = attentionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Attention not found with ID: " + id));
        if (attention != null) {
            Hibernate.initialize(attention.getAuthorizations());
            Hibernate.initialize(attention.getDiagnosticCodes());
        }
        assert attention != null;
        return attentionEnrichmentService.enrichAttentionResponseDto(attention);
    }

    @Transactional(readOnly = true)
    public List<PatientWithAttentionsResponse> getAttentionsByPatientId(Long patientId) {
        log.info("Fetching attentions for patient ID: {}", patientId);
        List<Attention> attentions = attentionRepository.findByPatientId(patientId);

        if (attentions != null) {
            attentions.forEach(attention -> {
                Hibernate.initialize(attention.getAuthorizations());
                Hibernate.initialize(attention.getDiagnosticCodes());
            });
        }

        String patientName = "";
        try {
            GetPatientDto patient = patientClient.getPatientByIdentificationNumber(patientId.toString());
            if (patient != null) {
                patientName = patient.name() + (patient.lastName() != null ? " " + patient.lastName() : "");
            }
        } catch (Exception e) {
            log.error("Error fetching patient name for id {}: {}", patientId, e.getMessage());
        }

        return List.of(
                attentionMapper.toPatientWithAttentionsResponse(patientName, attentions)
        );
    }

    @Transactional(readOnly = true)
    public List<PatientWithAttentionsResponse> getActiveAttentionByPatientId(Long patientId) {
        log.info("Fetching active attention for patient ID: {}", patientId);
        List<Attention> activeAttention = Collections.singletonList(attentionRepository.findByPatientIdAndStatus(patientId,
                        AttentionStatus.CREATED)
                .orElseThrow(() -> new EntityNotFoundException("No active attention found for patient ID: " + patientId)));

        activeAttention.forEach(attention -> {
            Hibernate.initialize(attention.getAuthorizations());
            Hibernate.initialize(attention.getDiagnosticCodes());
        });

        String patientName = "";
        try {
            GetPatientDto patient = patientClient.getPatientByIdentificationNumber(patientId.toString());
            if (patient != null) {
                patientName = patient.name() + (patient.lastName() != null ? " " + patient.lastName() : "");
            }
        } catch (Exception e) {
            log.error("Error fetching patient name for id {}: {}", patientId, e.getMessage());
        }

        return List.of(
                attentionMapper.toPatientWithAttentionsResponse(patientName, activeAttention)
        );
    }

    @Transactional(readOnly = true)
    public List<DoctorWithAttentionsResponse> getAttentionsByDoctorId(Long doctorId) {
        log.info("Fetching attentions for doctor ID: {}", doctorId);
        List<Attention> attentions = attentionRepository.findByDoctorId(doctorId);

        if (attentions != null) {
            attentions.forEach(attention -> {
                Hibernate.initialize(attention.getAuthorizations());
                Hibernate.initialize(attention.getDiagnosticCodes());
            });
        }

        String doctorName = "";
        try {
            GetDoctorDto doctor = doctorClient.getDoctorById(doctorId);
            if (doctor != null) {
                doctorName = doctor.name() + (doctor.lastName() != null ? " " + doctor.lastName() : "");
            }
        } catch (Exception e) {
            log.error("Error fetching doctor name for id {}: {}", doctorId, e.getMessage());
        }

        Function<Long, String> patientNameResolver = (id) -> {
            try {
                GetPatientDto patient = patientClient.getPatientByIdentificationNumber(id.toString());
                if (patient == null) return "";
                return patient.name() + (patient.lastName() != null ? " " + patient.lastName() : "");
            } catch (Exception e) {
                log.error("Error fetching patient name for id {}: {}", id, e.getMessage());
                return "";
            }
        };

        List<PatientWithAttentionsResponse> grouped = attentionMapper.groupAttentionsByPatient(
                attentions,
                patientNameResolver
        );

        return List.of(
                attentionMapper.toDoctorWithAttentionsResponse(doctorName, grouped));
    }

    @Transactional(readOnly = true)
    public List<HealthProviderWithAttentionsResponse> getGroupedAttentionsByHealthProvider(String healthProviderNit) {
        log.info("Fetching attentions for health provider NIT: {}", healthProviderNit);

        List<Attention> attentions = attentionRepository.findByHealthProviderNit(healthProviderNit);

        if (attentions != null) {
            attentions.forEach(attention -> {
                Hibernate.initialize(attention.getAuthorizations());
                Hibernate.initialize(attention.getDiagnosticCodes());
                Hibernate.initialize(attention.getUserHistory());
            });
        }

        Long contractId = null;
        if (!attentions.isEmpty() && attentions.get(0).getHealthProviderNit() != null && !attentions.get(0).getHealthProviderNit().isEmpty()) {
            HealthProviderInfo providerInfo = attentions.get(0).getHealthProviderNit().stream()
                    .filter(hp -> healthProviderNit.equals(hp.getHealthProviderNit()))
                    .findFirst()
                    .orElse(null);
            if (providerInfo != null) {
                contractId = providerInfo.getContractId();
            }
        }

        String contractName;
        try {
            GetHealthProviderDto dto = healthProviderClient.getHealthProviderByNitAndContract(healthProviderNit, contractId);
            contractName = dto.socialReason();
        } catch (Exception ex) {
            log.warn("No se encontró nombre social para NIT: {}", healthProviderNit);
            contractName = healthProviderNit;
        }

        HealthProviderWithAttentionsResponse response =
                attentionMapper.toHealthProviderWithAttentionsResponse(contractName, attentions);

        return List.of(response);
    }

    @Transactional(readOnly = true)
    public List<AttentionResponseDto> getAttentionsByConfigurationServiceId(Long configServiceId) {
        log.info("Fetching attentions for configuration service ID: {}", configServiceId);
        List<Attention> attentions = attentionRepository.findByConfigurationServiceId(configServiceId);

        if (attentions != null) {
            attentions.forEach(attention -> {
                Hibernate.initialize(attention.getAuthorizations());
                Hibernate.initialize(attention.getDiagnosticCodes());
            });
        }

        assert attentions != null;
        return attentions.stream()
                .map(attentionEnrichmentService::enrichAttentionResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<AttentionResponseDto> searchAttentions(AttentionSearchRequest searchRequest) {
        log.info("Performing attention search with criteria: {}", searchRequest);
        validateSearchRequest(searchRequest);

        Specification<Attention> spec = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (searchRequest.patientId() != null) {
            spec = spec.and(hasPatientId(searchRequest.patientId()));
        }
        if (searchRequest.doctorId() != null) {
            spec = spec.and(hasDoctorId(searchRequest.doctorId()));
        }
        if (searchRequest.healthProviderNit() != null && !searchRequest.healthProviderNit().isEmpty()) {
            spec = spec.and(hasHealthProviderNit(searchRequest.healthProviderNit()));
        }
        if (searchRequest.status() != null) {
            spec = spec.and(hasStatus(searchRequest.status()));
        }
        if (searchRequest.cause() != null) {
            spec = spec.and(hasCause(searchRequest.cause()));
        }
        if (searchRequest.entryMethod() != null && !searchRequest.entryMethod().isEmpty()) {
            spec = spec.and(hasEntryMethod(searchRequest.entryMethod()));
        }
        if (searchRequest.isReferral() != null) {
            spec = spec.and(isReferral(searchRequest.isReferral()));
        }
        if (searchRequest.triageLevel() != null) {
            spec = spec.and(hasTriageLevel(searchRequest.triageLevel()));
        }
        if (searchRequest.dischargeDateFrom() != null) {
            spec = spec.and(hasDischargeDateAfter(searchRequest.dischargeDateFrom()));
        }
        if (searchRequest.dischargeDateTo() != null) {
            spec = spec.and(hasDischargeDateBefore(searchRequest.dischargeDateTo()));
        }
        if (searchRequest.invoiced() != null) {
            spec = spec.and(isInvoiced(searchRequest.invoiced()));
        }
        if (searchRequest.configurationServiceId() != null) {
            spec = spec.and(hasConfigurationServiceId(searchRequest.configurationServiceId()));
        }
        // Puedes añadir más criterios de búsqueda aquí.

        Pageable pageable = createPageable(searchRequest);
        Page<Attention> attentionPage = attentionRepository.findAll(spec, pageable);

        List<AttentionResponseDto> content = attentionPage.getContent().stream()
                .map(attentionEnrichmentService::enrichAttentionResponseDto)
                .collect(Collectors.toList());

        return new PageImpl<>(content, pageable, attentionPage.getTotalElements());
    }

    private Pageable createPageable(AttentionSearchRequest searchRequest) {
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
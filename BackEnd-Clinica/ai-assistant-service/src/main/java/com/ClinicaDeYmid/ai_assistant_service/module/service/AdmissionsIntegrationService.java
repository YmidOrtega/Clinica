package com.ClinicaDeYmid.ai_assistant_service.module.service;

import com.ClinicaDeYmid.ai_assistant_service.module.dto.admissions.AttentionRequestDto;
import com.ClinicaDeYmid.ai_assistant_service.module.dto.admissions.AttentionResponseDto;
import com.ClinicaDeYmid.ai_assistant_service.module.feignclient.AdmissionsClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdmissionsIntegrationService {

    private final AdmissionsClient admissionsClient;

    /**
     * Crea una atención médica a través del servicio de admisiones
     */
    @CircuitBreaker(name = "ai-assistant-service", fallbackMethod = "createAttentionFallback")
    public AttentionResponseDto createAttention(AttentionRequestDto request) {
        try {
            log.info("Creating attention for patient ID: {}", request.patientId());
            AttentionResponseDto response = admissionsClient.createAttention(request);
            log.info("Successfully created attention with ID: {}", response.id());
            return response;
        } catch (Exception e) {
            log.error("Error creating attention for patient {}: {}", request.patientId(), e.getMessage(), e);
            throw new RuntimeException("Error creating attention: " + e.getMessage(), e);
        }
    }

    /**
     * Obtiene una atención por ID
     */
    @CircuitBreaker(name = "ai-assistant-service", fallbackMethod = "getAttentionFallback")
    public AttentionResponseDto getAttentionById(Long attentionId) {
        try {
            log.debug("Fetching attention with ID: {}", attentionId);
            AttentionResponseDto response = admissionsClient.getAttentionById(attentionId);
            log.debug("Successfully fetched attention with ID: {}", attentionId);
            return response;
        } catch (Exception e) {
            log.error("Error fetching attention {}: {}", attentionId, e.getMessage(), e);
            throw new RuntimeException("Error fetching attention: " + e.getMessage(), e);
        }
    }

    /**
     * Fallback para creación de atención
     */
    private AttentionResponseDto createAttentionFallback(AttentionRequestDto request, Throwable ex) {
        log.error("Circuit breaker activated for createAttention. Patient: {}. Error: {}",
                request.patientId(), ex.getMessage());
        throw new RuntimeException("Admissions service is currently unavailable. Please try again later.");
    }

    /**
     * Fallback para obtención de atención
     */
    private AttentionResponseDto getAttentionFallback(Long attentionId, Throwable ex) {
        log.error("Circuit breaker activated for getAttention. ID: {}. Error: {}",
                attentionId, ex.getMessage());
        throw new RuntimeException("Admissions service is currently unavailable. Please try again later.");
    }
}

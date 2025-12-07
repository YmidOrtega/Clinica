package com.ClinicaDeYmid.api_gateway.service;

import com.ClinicaDeYmid.api_gateway.entity.RequestLog;
import com.ClinicaDeYmid.api_gateway.repository.RequestLogRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Servicio para gestionar el logging de peticiones
 * Guarda información de todas las peticiones en base de datos para analytics
 */
@Service
public class RequestLogService {

    private static final Logger logger = Logger.getLogger(RequestLogService.class.getName());
    private final RequestLogRepository requestLogRepository;

    public RequestLogService(RequestLogRepository requestLogRepository) {
        this.requestLogRepository = requestLogRepository;
    }

    /**
     * Guarda un log de petición de forma asíncrona
     * No bloquea la respuesta al cliente
     */
    @Async
    @Transactional
    public void logRequest(
            String userId,
            String userEmail,
            String endpoint,
            String httpMethod,
            Integer statusCode,
            Long durationMs,
            String ipAddress,
            String userAgent,
            String serviceName,
            String errorMessage
    ) {
        try {
            RequestLog requestLog = RequestLog.builder()
                    .userId(userId)
                    .userEmail(userEmail)
                    .endpoint(endpoint)
                    .httpMethod(httpMethod)
                    .statusCode(statusCode)
                    .timestamp(LocalDateTime.now())
                    .durationMs(durationMs)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .serviceName(serviceName)
                    .errorMessage(errorMessage)
                    .build();

            requestLogRepository.save(requestLog);

            logger.fine("Request log saved: " + endpoint + " - " + statusCode);
        } catch (Exception e) {
            // No lanzar excepción para no afectar la respuesta
            logger.warning("Error saving request log: " + e.getMessage());
        }
    }

    /**
     * Versión simplificada para logging rápido
     */
    @Async
    @Transactional
    public void logRequest(
            String userId,
            String endpoint,
            String httpMethod,
            Integer statusCode,
            Long durationMs,
            String ipAddress
    ) {
        logRequest(userId, null, endpoint, httpMethod, statusCode, 
                  durationMs, ipAddress, null, extractServiceName(endpoint), null);
    }

    /**
     * Extrae el nombre del servicio desde el endpoint
     */
    private String extractServiceName(String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) {
            return "unknown";
        }

        // Formato esperado: /api/v1/service-name/...
        String[] parts = endpoint.split("/");
        if (parts.length >= 4) {
            return parts[3];
        }

        return "unknown";
    }
}

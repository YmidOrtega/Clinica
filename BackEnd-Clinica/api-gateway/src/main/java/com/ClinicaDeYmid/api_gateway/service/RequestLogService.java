package com.ClinicaDeYmid.api_gateway.service;

import com.ClinicaDeYmid.api_gateway.entity.RequestLog;
import com.ClinicaDeYmid.api_gateway.repository.RequestLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestLogService {

    private final RequestLogRepository requestLogRepository;

    @Async("requestLogExecutor")
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

            log.debug("Request log saved: {} - {}", endpoint, statusCode);
        } catch (Exception e) {
            log.warn("Error saving request log: {}", e.getMessage());
        }
    }

    @Async("requestLogExecutor")
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

    private String extractServiceName(String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) {
            return "unknown";
        }
        String[] parts = endpoint.split("/");
        if (parts.length >= 4) {
            return parts[3];
        }
        return "unknown";
    }
}

package com.ClinicaDeYmid.api_gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO para respuestas de analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsResponse {
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Long totalRequests;
    private Long successfulRequests;
    private Long errorRequests;
    private Double errorRate;
    private Double averageLatencyMs;
    private Map<String, Object> byService;
    private List<EndpointStats> topEndpoints;
    private List<UserStats> topUsers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EndpointStats {
        private String endpoint;
        private String method;
        private Long requestCount;
        private Double averageLatencyMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserStats {
        private String userId;
        private String userEmail;
        private Long requestCount;
        private LocalDateTime lastRequest;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceStats {
        private String serviceName;
        private Long requestCount;
        private Long errorCount;
        private Double errorRate;
        private Double averageLatencyMs;
    }
}

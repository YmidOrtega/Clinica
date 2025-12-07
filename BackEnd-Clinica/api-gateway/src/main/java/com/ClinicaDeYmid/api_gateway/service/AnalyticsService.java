package com.ClinicaDeYmid.api_gateway.service;

import com.ClinicaDeYmid.api_gateway.dto.AnalyticsResponse;
import com.ClinicaDeYmid.api_gateway.repository.RequestLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Servicio para generar analytics y estadísticas de las peticiones
 */
@Service
public class AnalyticsService {

    private final RequestLogRepository requestLogRepository;

    public AnalyticsService(RequestLogRepository requestLogRepository) {
        this.requestLogRepository = requestLogRepository;
    }

    /**
     * Genera un resumen general de analytics
     */
    public AnalyticsResponse getOverview(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> endpointStats = requestLogRepository.getEndpointStatistics(startDate, endDate);
        List<Object[]> errorStats = requestLogRepository.getErrorStatistics(startDate, endDate);
        
        long totalRequests = endpointStats.stream()
                .mapToLong(row -> ((Number) row[1]).longValue())
                .sum();
        
        long errorRequests = errorStats.stream()
                .mapToLong(row -> ((Number) row[1]).longValue())
                .sum();
        
        long successfulRequests = totalRequests - errorRequests;
        double errorRate = totalRequests > 0 ? (errorRequests * 100.0 / totalRequests) : 0.0;

        return AnalyticsResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalRequests(totalRequests)
                .successfulRequests(successfulRequests)
                .errorRequests(errorRequests)
                .errorRate(Math.round(errorRate * 100.0) / 100.0)
                .build();
    }

    /**
     * Obtiene estadísticas agrupadas por servicio
     */
    public Map<String, Object> getStatsByService(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> serviceStats = requestLogRepository.getAverageLatencyByService(startDate, endDate);
        
        Map<String, Object> result = new HashMap<>();
        for (Object[] row : serviceStats) {
            String serviceName = (String) row[0];
            Double avgLatency = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
            
            Map<String, Object> serviceData = new HashMap<>();
            serviceData.put("serviceName", serviceName);
            serviceData.put("averageLatencyMs", Math.round(avgLatency * 100.0) / 100.0);
            
            result.put(serviceName, serviceData);
        }
        
        return result;
    }

    /**
     * Obtiene los endpoints más utilizados
     */
    public List<AnalyticsResponse.EndpointStats> getTopEndpoints(int limit) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(7);
        LocalDateTime endDate = LocalDateTime.now();
        
        List<Object[]> stats = requestLogRepository.getEndpointStatistics(startDate, endDate);
        
        return stats.stream()
                .limit(limit)
                .map(row -> AnalyticsResponse.EndpointStats.builder()
                        .endpoint((String) row[0])
                        .requestCount(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Obtiene estadísticas de errores
     */
    public Map<String, Long> getErrorStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> errorStats = requestLogRepository.getErrorStatistics(startDate, endDate);
        
        Map<String, Long> result = new HashMap<>();
        for (Object[] row : errorStats) {
            Integer statusCode = (Integer) row[0];
            Long count = ((Number) row[1]).longValue();
            result.put("HTTP_" + statusCode, count);
        }
        
        return result;
    }

    /**
     * Obtiene latencia promedio por servicio
     */
    public Map<String, Double> getLatencyByService(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> latencyStats = requestLogRepository.getAverageLatencyByService(startDate, endDate);
        
        Map<String, Double> result = new HashMap<>();
        for (Object[] row : latencyStats) {
            String serviceName = (String) row[0];
            Double avgLatency = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
            result.put(serviceName, Math.round(avgLatency * 100.0) / 100.0);
        }
        
        return result;
    }

    /**
     * Obtiene los usuarios más activos
     */
    public List<AnalyticsResponse.UserStats> getTopUsers(int limit, LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> userLogs = requestLogRepository.getEndpointStatistics(startDate, endDate);
        
        // Esta es una implementación simplificada
        // En producción, deberías crear una query específica en el repositorio
        return List.of();
    }
}

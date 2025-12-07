package com.ClinicaDeYmid.api_gateway.controller;

import com.ClinicaDeYmid.api_gateway.dto.AnalyticsResponse;
import com.ClinicaDeYmid.api_gateway.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Controller para exponer analytics de las peticiones del API Gateway
 * Permite consultar estadísticas, métricas y logs históricos
 */
@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Analytics", description = "Endpoints para analytics y métricas del API Gateway")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Obtiene estadísticas generales de peticiones en un rango de fechas
     */
    @GetMapping("/overview")
    @Operation(summary = "Estadísticas generales", description = "Obtiene un resumen de todas las peticiones")
    public ResponseEntity<AnalyticsResponse> getOverview(
            @Parameter(description = "Fecha de inicio (ISO format)")
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime startDate,
            
            @Parameter(description = "Fecha de fin (ISO format)")
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime endDate
    ) {
        if (startDate == null) {
            startDate = LocalDateTime.now().minusDays(7);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }
        
        return ResponseEntity.ok(analyticsService.getOverview(startDate, endDate));
    }

    /**
     * Obtiene estadísticas por servicio
     */
    @GetMapping("/by-service")
    @Operation(summary = "Estadísticas por servicio", description = "Métricas agrupadas por servicio")
    public ResponseEntity<?> getStatsByService(
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime startDate,
            
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime endDate
    ) {
        if (startDate == null) {
            startDate = LocalDateTime.now().minusDays(7);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }
        
        return ResponseEntity.ok(analyticsService.getStatsByService(startDate, endDate));
    }

    /**
     * Obtiene los endpoints más utilizados
     */
    @GetMapping("/top-endpoints")
    @Operation(summary = "Endpoints más usados", description = "Lista de endpoints ordenados por uso")
    public ResponseEntity<?> getTopEndpoints(
            @Parameter(description = "Número de resultados")
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(analyticsService.getTopEndpoints(limit));
    }

    /**
     * Obtiene estadísticas de errores
     */
    @GetMapping("/errors")
    @Operation(summary = "Estadísticas de errores", description = "Análisis de errores por código de estado")
    public ResponseEntity<?> getErrorStats(
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime startDate,
            
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime endDate
    ) {
        if (startDate == null) {
            startDate = LocalDateTime.now().minusDays(7);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }
        
        return ResponseEntity.ok(analyticsService.getErrorStatistics(startDate, endDate));
    }

    /**
     * Obtiene latencia promedio por servicio
     */
    @GetMapping("/latency")
    @Operation(summary = "Latencia por servicio", description = "Métricas de latencia promedio")
    public ResponseEntity<?> getLatencyStats(
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime startDate,
            
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime endDate
    ) {
        if (startDate == null) {
            startDate = LocalDateTime.now().minusDays(7);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }
        
        return ResponseEntity.ok(analyticsService.getLatencyByService(startDate, endDate));
    }

    /**
     * Obtiene usuarios más activos
     */
    @GetMapping("/top-users")
    @Operation(summary = "Usuarios más activos", description = "Lista de usuarios con más peticiones")
    public ResponseEntity<?> getTopUsers(
            @Parameter(description = "Número de resultados")
            @RequestParam(defaultValue = "10") int limit,
            
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime startDate,
            
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime endDate
    ) {
        if (startDate == null) {
            startDate = LocalDateTime.now().minusDays(7);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }
        
        return ResponseEntity.ok(analyticsService.getTopUsers(limit, startDate, endDate));
    }
}

package com.ClinicaDeYmid.api_gateway.repository;

import com.ClinicaDeYmid.api_gateway.entity.RequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio para gestionar los logs de peticiones
 * Proporciona queries para analytics y auditoría
 */
@Repository
public interface RequestLogRepository extends JpaRepository<RequestLog, Long> {

    /**
     * Encuentra logs por usuario en un rango de fechas
     */
    List<RequestLog> findByUserIdAndTimestampBetween(
            String userId, 
            LocalDateTime start, 
            LocalDateTime end
    );

    /**
     * Encuentra logs por endpoint en un rango de fechas
     */
    List<RequestLog> findByEndpointAndTimestampBetween(
            String endpoint, 
            LocalDateTime start, 
            LocalDateTime end
    );

    /**
     * Obtiene el conteo de peticiones por endpoint
     */
    @Query("SELECT r.endpoint, COUNT(r) FROM RequestLog r " +
           "WHERE r.timestamp BETWEEN :start AND :end " +
           "GROUP BY r.endpoint ORDER BY COUNT(r) DESC")
    List<Object[]> getEndpointStatistics(
            @Param("start") LocalDateTime start, 
            @Param("end") LocalDateTime end
    );

    /**
     * Obtiene el conteo de errores por código de estado
     */
    @Query("SELECT r.statusCode, COUNT(r) FROM RequestLog r " +
           "WHERE r.statusCode >= 400 AND r.timestamp BETWEEN :start AND :end " +
           "GROUP BY r.statusCode ORDER BY COUNT(r) DESC")
    List<Object[]> getErrorStatistics(
            @Param("start") LocalDateTime start, 
            @Param("end") LocalDateTime end
    );

    /**
     * Obtiene latencia promedio por servicio
     */
    @Query("SELECT r.serviceName, AVG(r.durationMs) FROM RequestLog r " +
           "WHERE r.timestamp BETWEEN :start AND :end " +
           "GROUP BY r.serviceName")
    List<Object[]> getAverageLatencyByService(
            @Param("start") LocalDateTime start, 
            @Param("end") LocalDateTime end
    );

    /**
     * Cuenta peticiones por IP en un rango de tiempo (para rate limiting)
     */
    @Query("SELECT COUNT(r) FROM RequestLog r " +
           "WHERE r.ipAddress = :ipAddress AND r.timestamp > :since")
    Long countByIpAddressSince(
            @Param("ipAddress") String ipAddress, 
            @Param("since") LocalDateTime since
    );
}

package com.ClinicaDeYmid.auth_service.module.auth.repository;

import com.ClinicaDeYmid.auth_service.module.auth.entity.AuditLog;
import com.ClinicaDeYmid.auth_service.module.auth.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Busca logs por usuario (paginado)
     */
    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(
            Long userId,
            Pageable pageable
    );

    /**
     * Busca logs por email (paginado)
     */
    Page<AuditLog> findByUserEmailOrderByCreatedAtDesc(
            String userEmail,
            Pageable pageable
    );

    /**
     * Busca logs por acción (paginado)
     */
    Page<AuditLog> findByActionOrderByCreatedAtDesc(
            AuditAction action,
            Pageable pageable
    );

    /**
     * Busca logs por rango de fechas
     */
    @Query("""
        SELECT al FROM AuditLog al
        WHERE al.createdAt BETWEEN :startDate AND :endDate
        ORDER BY al.createdAt DESC
        """)
    Page<AuditLog> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    /**
     * Busca logs por usuario y acción
     */
    @Query("""
        SELECT al FROM AuditLog al
        WHERE al.userId = :userId
        AND al.action = :action
        ORDER BY al.createdAt DESC
        """)
    List<AuditLog> findByUserIdAndAction(
            @Param("userId") Long userId,
            @Param("action") AuditAction action
    );

    /**
     * Busca logs por IP
     */
    Page<AuditLog> findByIpAddressOrderByCreatedAtDesc(
            String ipAddress,
            Pageable pageable
    );

    /**
     * Cuenta acciones por tipo en un período
     */
    @Query("""
        SELECT al.action, COUNT(al) FROM AuditLog al
        WHERE al.createdAt > :since
        GROUP BY al.action
        """)
    List<Object[]> countActionsSince(@Param("since") LocalDateTime since);

    /**
     * Elimina logs antiguos (limpieza)
     */
    void deleteByCreatedAtBefore(LocalDateTime before);

    /**
     * Busca actividad sospechosa (muchas acciones fallidas)
     */
    @Query("""
        SELECT al.ipAddress, COUNT(al) as failureCount FROM AuditLog al
        WHERE al.action IN (:failureActions)
        AND al.createdAt > :since
        GROUP BY al.ipAddress
        HAVING COUNT(al) > :threshold
        ORDER BY failureCount DESC
        """)
    List<Object[]> findSuspiciousActivity(
            @Param("failureActions") List<AuditAction> failureActions,
            @Param("since") LocalDateTime since,
            @Param("threshold") long threshold
    );
}
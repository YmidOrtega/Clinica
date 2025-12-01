package com.ClinicaDeYmid.ai_assistant_service.module.repository;

import com.ClinicaDeYmid.ai_assistant_service.module.entity.ConversationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationHistoryRepository extends JpaRepository<ConversationHistory, Long> {

    /**
     * Busca la conversación activa del usuario
     */
    Optional<ConversationHistory> findByUserIdAndIsActiveTrue(Long userId);

    /**
     * Busca conversación por session ID
     */
    Optional<ConversationHistory> findBySessionId(String sessionId);

    /**
     * Lista todas las conversaciones de un usuario
     */
    List<ConversationHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Lista de conversaciones activas de un usuario
     */
    List<ConversationHistory> findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(Long userId);

    /**
     * Busca conversaciones por rango de fechas
     */
    @Query("SELECT c FROM ConversationHistory c WHERE c.userId = :userId " +
            "AND c.createdAt BETWEEN :startDate AND :endDate " +
            "ORDER BY c.createdAt DESC")
    List<ConversationHistory> findByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Cuenta conversaciones activas de un usuario
     */
    long countByUserIdAndIsActiveTrue(Long userId);
}
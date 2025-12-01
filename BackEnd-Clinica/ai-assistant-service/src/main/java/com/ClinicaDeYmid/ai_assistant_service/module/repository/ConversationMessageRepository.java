package com.ClinicaDeYmid.ai_assistant_service.module.repository;

import com.ClinicaDeYmid.ai_assistant_service.module.entity.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    /**
     * Lista de mensajes de una conversación ordenados cronológicamente
     */
    List<ConversationMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    /**
     * Cuenta mensajes de una conversación
     */
    long countByConversationId(Long conversationId);

    /**
     * Obtiene los últimos N mensajes de una conversación
     */
    @Query("SELECT m FROM ConversationMessage m WHERE m.conversation.id = :conversationId " +
            "ORDER BY m.createdAt DESC")
    List<ConversationMessage> findLastNMessages(
            @Param("conversationId") Long conversationId,
            @Param("limit") int limit
    );

    /**
     * Busca mensajes por rol en una conversación
     */
    List<ConversationMessage> findByConversationIdAndRoleOrderByCreatedAtAsc(
            Long conversationId,
            ConversationMessage.MessageRole role
    );
}
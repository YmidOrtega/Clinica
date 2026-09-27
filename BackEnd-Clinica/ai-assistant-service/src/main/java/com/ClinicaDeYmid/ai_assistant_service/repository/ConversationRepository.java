package com.ClinicaDeYmid.ai_assistant_service.repository;

import com.ClinicaDeYmid.ai_assistant_service.repository.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByUuidAndOwnerUuid(UUID uuid, UUID ownerUuid);

    Page<Conversation> findByOwnerUuidOrderByUpdatedAtDesc(UUID ownerUuid, Pageable pageable);

    @Query(value = "SELECT assistant.purge_conversations(:cutoff)", nativeQuery = true)
    int purgeUntouchedSince(@Param("cutoff") Instant cutoff);
}

package com.ClinicaDeYmid.ai_assistant_service.repository;

import com.ClinicaDeYmid.ai_assistant_service.repository.entity.ProposedAction;
import com.ClinicaDeYmid.ai_assistant_service.shared.ActionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProposedActionRepository extends JpaRepository<ProposedAction, Long> {

    Optional<ProposedAction> findByUuidAndOwnerUuid(UUID uuid, UUID ownerUuid);

    List<ProposedAction> findTop50ByOwnerUuidAndStatusOrderByProposedAtDesc(UUID ownerUuid, ActionStatus status);

    List<ProposedAction> findTop50ByOwnerUuidOrderByProposedAtDesc(UUID ownerUuid);
}

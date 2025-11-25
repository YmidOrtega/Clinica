package com.ClinicaDeYmid.admissions_service.module.service;

import com.ClinicaDeYmid.admissions_service.infra.exception.EntityNotFoundException;
import com.ClinicaDeYmid.admissions_service.infra.exception.ValidationException;
import com.ClinicaDeYmid.admissions_service.infra.security.UserContextHolder;
import com.ClinicaDeYmid.admissions_service.module.entity.Attention;
import com.ClinicaDeYmid.admissions_service.module.repository.AttentionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttentionStatusService {

    private final AttentionRepository attentionRepository;

    /**
     * Activa una atención
     */
    @Transactional
    @CacheEvict(value = "attentions", key = "#id")
    public void activateAttention(Long id) {
        log.info("Activating attention with ID: {}", id);

        Attention attention = attentionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Attention not found with ID: " + id));

        if (attention.isDeleted()) {
            throw new ValidationException("Cannot activate a soft-deleted attention. Restore it first.");
        }

        attention.setActive(true);
        attentionRepository.save(attention);

        log.info("Attention with ID {} activated successfully", id);
    }

    /**
     * Desactiva una atención
     */
    @Transactional
    @CacheEvict(value = "attentions", key = "#id")
    public void deactivateAttention(Long id) {
        log.info("Deactivating attention with ID: {}", id);

        Attention attention = attentionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Attention not found with ID: " + id));

        if (attention.isDeleted()) {
            throw new ValidationException("Cannot deactivate a soft-deleted attention.");
        }

        attention.setActive(false);
        attentionRepository.save(attention);

        log.info("Attention with ID {} deactivated successfully", id);
    }

    /**
     * Soft delete de una atención
     */
    @Transactional
    @CacheEvict(value = "attentions", key = "#id")
    public void softDeleteAttention(Long id, String reason) {
        log.info("Soft deleting attention with ID: {} for reason: {}", id, reason);

        Attention attention = attentionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Attention not found with ID: " + id));

        if (attention.isDeleted()) {
            throw new ValidationException("Attention is already soft-deleted");
        }

        // Validar que la atención se pueda eliminar
        if (attention.isInvoiced()) {
            throw new ValidationException("Cannot delete an invoiced attention");
        }

        Long currentUserId = UserContextHolder.getCurrentUserId();
        if (currentUserId == null) {
            throw new ValidationException("User context not available for soft delete operation");
        }

        attention.softDelete(currentUserId, reason);
        attentionRepository.save(attention);

        log.info("Attention with ID {} soft deleted successfully by user {}", id, currentUserId);
    }

    /**
     * Restaura una atención soft-deleted
     */
    @Transactional
    @CacheEvict(value = "attentions", key = "#id")
    public void restoreAttention(Long id) {
        log.info("Restoring soft-deleted attention with ID: {}", id);

        // Buscar incluyendo soft-deleted
        Attention attention = attentionRepository.findByIdIncludingDeleted(id)
                .orElseThrow(() -> new EntityNotFoundException("Attention not found with ID: " + id));

        if (!attention.isDeleted()) {
            throw new ValidationException("Attention is not soft-deleted, cannot restore");
        }

        attention.restore();
        attentionRepository.save(attention);

        log.info("Attention with ID {} restored successfully", id);
    }
}
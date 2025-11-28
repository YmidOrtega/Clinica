package com.ClinicaDeYmid.suppliers_service.module.service;

import com.ClinicaDeYmid.suppliers_service.infra.security.UserContextHolder;
import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import com.ClinicaDeYmid.suppliers_service.module.repository.DoctorRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DoctorStatusService {

    private final DoctorRepository doctorRepository;

    private Long getCurrentUserId() {
        Long userId = UserContextHolder.getCurrentUserId();
        if (userId == null) {
            log.warn("No se pudo obtener userId del contexto de seguridad, usando fallback");
            return 1L;
        }
        return userId;
    }

    @CacheEvict(value = "doctor-entities", key = "#id")
    @Transactional
    public void activateDoctor(Long id) {
        log.info("Activating doctor with ID: {}", id);
        log.debug("🗑️ Invalidando cache para doctor: {}", id);

        Long userId = getCurrentUserId();

        Doctor doctor = doctorRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Doctor not found with id: " + id));

        doctor.setActive(true);
        doctor.setUpdatedBy(userId);

        doctorRepository.save(doctor);
        log.info("Doctor activated successfully by user: {}", userId);
    }

    @CacheEvict(value = "doctor-entities", key = "#id")
    @Transactional
    public void deactivateDoctor(Long id) {
        log.info("Deactivating doctor with ID: {}", id);
        log.debug("🗑️ Invalidando cache para doctor: {}", id);

        Long userId = getCurrentUserId();

        Doctor doctor = doctorRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Doctor not found with id: " + id));

        doctor.setActive(false);
        doctor.setUpdatedBy(userId);

        doctorRepository.save(doctor);
        log.info("Doctor deactivated successfully by user: {}", userId);
    }

    @CacheEvict(value = "doctor-entities", key = "#id")
    @Transactional
    public void softDeleteDoctor(Long id, String reason) {
        log.info("Soft deleting doctor with ID: {} for reason: {}", id, reason);
        log.debug("🗑️ Invalidando cache para doctor: {}", id);

        Long userId = getCurrentUserId();

        Doctor doctor = doctorRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Doctor not found with id: " + id));

        doctor.setDeletedBy(userId);
        doctor.setDeletionReason(reason != null ? reason : "No reason provided");

        doctorRepository.delete(doctor);
        log.info("Doctor soft deleted successfully by user: {}", userId);
    }

    @CacheEvict(value = "doctor-entities", key = "#id")
    @Transactional
    public void restoreDoctor(Long id) {
        log.info("Restoring doctor with ID: {}", id);

        Long userId = getCurrentUserId();

        // Buscar incluyendo eliminados
        Doctor doctor = doctorRepository.findByIdIncludingDeleted(id)
                .orElseThrow(() -> new EntityNotFoundException("Doctor not found with id: " + id));

        if (doctor.getDeletedAt() == null) {
            throw new IllegalStateException("Doctor is not deleted");
        }

        doctor.setDeletedAt(null);
        doctor.setDeletedBy(null);
        doctor.setDeletionReason(null);
        doctor.setActive(true);
        doctor.setUpdatedBy(userId);

        doctorRepository.save(doctor);
        log.info("Doctor restored successfully by user: {}", userId);
    }
}
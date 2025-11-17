package com.ClinicaDeYmid.patient_service.module.service;

import com.ClinicaDeYmid.patient_service.infra.exception.BusinessException;
import com.ClinicaDeYmid.patient_service.infra.exception.ResourceNotFoundException;
import com.ClinicaDeYmid.patient_service.module.dto.allergy.AllergyRequestDTO;
import com.ClinicaDeYmid.patient_service.module.dto.allergy.AllergyResponseDTO;
import com.ClinicaDeYmid.patient_service.module.dto.allergy.AllergySummaryDTO;
import com.ClinicaDeYmid.patient_service.module.dto.allergy.AllergyUpdateDTO;
import com.ClinicaDeYmid.patient_service.module.entity.Allergy;
import com.ClinicaDeYmid.patient_service.module.entity.Patient;
import com.ClinicaDeYmid.patient_service.module.enums.AllergySeverity;
import com.ClinicaDeYmid.patient_service.module.mapper.AllergyMapper;
import com.ClinicaDeYmid.patient_service.module.repository.AllergyRepository;
import com.ClinicaDeYmid.patient_service.module.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AllergyService {

    private final AllergyRepository allergyRepository;
    private final PatientRepository patientRepository;
    private final AllergyMapper allergyMapper;

    /**
     * Crea una nueva alergia para un paciente
     */
    @CacheEvict(value = "patientAllergies", key = "#patientId")
    public AllergyResponseDTO create(Long patientId, AllergyRequestDTO requestDTO, Long userId) {
        log.info("Creating allergy for patient: {}", patientId);

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado con ID: " + patientId));

        // Validar alergia crítica no verificada
        if ((requestDTO.severity() == AllergySeverity.SEVERE ||
                requestDTO.severity() == AllergySeverity.LIFE_THREATENING) &&
                !Boolean.TRUE.equals(requestDTO.verified())) {
            log.warn("Creating critical allergy without verification for patient: {}", patientId);
        }

        Allergy allergy = allergyMapper.toEntity(requestDTO);
        allergy.setPatient(patient);
        allergy.setCreatedBy(userId);
        allergy.setUpdatedBy(userId);

        Allergy saved = allergyRepository.save(allergy);
        log.info("Allergy created successfully with ID: {}", saved.getId());

        return allergyMapper.toResponseDTO(saved);
    }

    /**
     * Obtiene una alergia por ID
     */
    @Transactional(readOnly = true)
    public AllergyResponseDTO getById(Long allergyId) {
        log.debug("Fetching allergy with ID: {}", allergyId);

        Allergy allergy = allergyRepository.findById(allergyId)
                .orElseThrow(() -> new ResourceNotFoundException("Alergia no encontrada con ID: " + allergyId));

        return allergyMapper.toResponseDTO(allergy);
    }

    /**
     * Obtiene todas las alergias activas de un paciente
     */
    @Cacheable(value = "patientAllergies", key = "#patientId")
    @Transactional(readOnly = true)
    public List<AllergySummaryDTO> getAllByPatientId(Long patientId) {
        log.debug("Fetching active allergies for patient: {}", patientId);

        List<Allergy> allergies = allergyRepository.findByPatientIdAndActiveTrue(patientId);
        return allergyMapper.toSummaryDTOList(allergies);
    }

    /**
     * Obtiene alergias de un paciente con paginación
     */
    @Transactional(readOnly = true)
    public Page<AllergyResponseDTO> getAllByPatientIdPaginated(Long patientId, Pageable pageable) {
        log.debug("Fetching paginated allergies for patient: {}", patientId);

        return allergyRepository.findByPatientIdAndActiveTrue(patientId, pageable)
                .map(allergyMapper::toResponseDTO);
    }

    /**
     * Obtiene alergias críticas de un paciente
     */
    @Transactional(readOnly = true)
    public List<AllergyResponseDTO> getCriticalAllergies(Long patientId) {
        log.debug("Fetching critical allergies for patient: {}", patientId);

        return allergyRepository.findCriticalAllergiesByPatientId(patientId)
                .stream()
                .map(allergyMapper::toResponseDTO)
                .toList();
    }

    /**
     * Obtiene alergias no verificadas
     */
    @Transactional(readOnly = true)
    public List<AllergyResponseDTO> getUnverifiedAllergies() {
        log.debug("Fetching unverified allergies");

        return allergyRepository.findUnverifiedAllergies()
                .stream()
                .map(allergyMapper::toResponseDTO)
                .toList();
    }

    /**
     * Actualiza una alergia
     */
    @CacheEvict(value = "patientAllergies", key = "#result.patientId")
    public AllergyResponseDTO update(Long allergyId, AllergyUpdateDTO updateDTO, Long userId) {
        log.info("Updating allergy with ID: {}", allergyId);

        Allergy allergy = allergyRepository.findById(allergyId)
                .orElseThrow(() -> new ResourceNotFoundException("Alergia no encontrada con ID: " + allergyId));

        allergyMapper.updateEntityFromDTO(updateDTO, allergy);
        allergy.setUpdatedBy(userId);

        Allergy updated = allergyRepository.save(allergy);
        log.info("Allergy updated successfully: {}", allergyId);

        return allergyMapper.toResponseDTO(updated);
    }

    /**
     * Marca una alergia como verificada
     */
    @CacheEvict(value = "patientAllergies", key = "#result.patientId")
    public AllergyResponseDTO verify(Long allergyId, Long userId) {
        log.info("Verifying allergy with ID: {}", allergyId);

        Allergy allergy = allergyRepository.findById(allergyId)
                .orElseThrow(() -> new ResourceNotFoundException("Alergia no encontrada con ID: " + allergyId));

        allergy.setVerified(true);
        allergy.setUpdatedBy(userId);

        Allergy verified = allergyRepository.save(allergy);
        log.info("Allergy verified successfully: {}", allergyId);

        return allergyMapper.toResponseDTO(verified);
    }

    /**
     * Desactiva una alergia (soft delete)
     */
    @CacheEvict(value = "patientAllergies", key = "#result.patientId")
    public AllergyResponseDTO deactivate(Long allergyId, Long userId) {
        log.info("Deactivating allergy with ID: {}", allergyId);

        Allergy allergy = allergyRepository.findById(allergyId)
                .orElseThrow(() -> new ResourceNotFoundException("Alergia no encontrada con ID: " + allergyId));

        allergy.setActive(false);
        allergy.setUpdatedBy(userId);

        Allergy deactivated = allergyRepository.save(allergy);
        log.info("Allergy deactivated successfully: {}", allergyId);

        return allergyMapper.toResponseDTO(deactivated);
    }

    /**
     * Elimina permanentemente una alergia
     */
    public void delete(Long allergyId, Long patientId) {
        log.warn("Permanently deleting allergy with ID: {}", allergyId);

        Allergy allergy = allergyRepository.findById(allergyId)
                .orElseThrow(() -> new ResourceNotFoundException("Alergia no encontrada con ID: " + allergyId));

        if (!allergy.getPatient().getId().equals(patientId)) {
            throw new BusinessException("La alergia no pertenece al paciente especificado");
        }

        allergyRepository.delete(allergy);
        log.info("Allergy permanently deleted: {}", allergyId);
    }

    /**
     * Verifica si un paciente tiene alergias críticas
     */
    @Transactional(readOnly = true)
    public boolean hasCriticalAllergies(Long patientId) {
        return allergyRepository.hasCriticalAllergies(patientId);
    }

    /**
     * Cuenta alergias activas de un paciente
     */
    @Transactional(readOnly = true)
    public long countActiveAllergies(Long patientId) {
        return allergyRepository.countByPatientIdAndActiveTrue(patientId);
    }
}
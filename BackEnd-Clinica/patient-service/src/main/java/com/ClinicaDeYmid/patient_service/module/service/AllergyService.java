package com.ClinicaDeYmid.patient_service.module.service;

import com.ClinicaDeYmid.patient_service.infra.exception.BusinessException;
import com.ClinicaDeYmid.patient_service.infra.exception.ResourceNotFoundException;
import com.ClinicaDeYmid.patient_service.infra.security.UserContextHolder;
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
     * Obtiene el userId del contexto de seguridad actual
     */
    private Long getCurrentUserId() {
        Long userId = UserContextHolder.getCurrentUserId();
        if (userId == null) {
            log.warn("No se pudo obtener userId del contexto de seguridad, usando fallback");
            return 1L;
        }
        return userId;
    }

    /**
     * Crea una nueva alergia para un paciente
     */
    @CacheEvict(value = "patientAllergies", key = "#patientId")
    public AllergyResponseDTO create(Long patientId, AllergyRequestDTO requestDTO) {
        log.info("Creating allergy for patient: {}", patientId);

        Long userId = getCurrentUserId();

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado con ID: " + patientId));

        if ((requestDTO.severity() == AllergySeverity.SEVERE ||
                requestDTO.severity() == AllergySeverity.LIFE_THREATENING) &&
                !Boolean.TRUE.equals(requestDTO.verified())) {
            log.warn("Creating critical allergy without verification for patient: {} by user: {}",
                    patientId, userId);
        }

        Allergy allergy = allergyMapper.toEntity(requestDTO);
        allergy.setPatient(patient);
        allergy.setCreatedBy(userId);
        allergy.setUpdatedBy(userId);

        Allergy saved = allergyRepository.save(allergy);
        log.info("Allergy created successfully with ID: {} by user: {}", saved.getId(), userId);

        return allergyMapper.toResponseDTO(saved);
    }

    @Transactional(readOnly = true)
    public AllergyResponseDTO getById(Long allergyId) {
        log.debug("Fetching allergy with ID: {}", allergyId);

        Allergy allergy = allergyRepository.findById(allergyId)
                .orElseThrow(() -> new ResourceNotFoundException("Alergia no encontrada con ID: " + allergyId));

        return allergyMapper.toResponseDTO(allergy);
    }

    @Cacheable(value = "patientAllergies", key = "#patientId")
    @Transactional(readOnly = true)
    public List<AllergySummaryDTO> getAllByPatientId(Long patientId) {
        log.debug("Fetching active allergies for patient: {}", patientId);

        List<Allergy> allergies = allergyRepository.findByPatientIdAndActiveTrue(patientId);
        return allergyMapper.toSummaryDTOList(allergies);
    }

    @Transactional(readOnly = true)
    public Page<AllergyResponseDTO> getAllByPatientIdPaginated(Long patientId, Pageable pageable) {
        log.debug("Fetching paginated allergies for patient: {}", patientId);

        return allergyRepository.findByPatientIdAndActiveTrue(patientId, pageable)
                .map(allergyMapper::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public List<AllergyResponseDTO> getCriticalAllergies(Long patientId) {
        log.debug("Fetching critical allergies for patient: {}", patientId);

        return allergyRepository.findCriticalAllergiesByPatientId(patientId)
                .stream()
                .map(allergyMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AllergyResponseDTO> getUnverifiedAllergies() {
        log.debug("Fetching unverified allergies");

        return allergyRepository.findUnverifiedAllergies()
                .stream()
                .map(allergyMapper::toResponseDTO)
                .toList();
    }

    @CacheEvict(value = "patientAllergies", key = "#result.patientId")
    public AllergyResponseDTO update(Long allergyId, AllergyUpdateDTO updateDTO) {
        log.info("Updating allergy with ID: {}", allergyId);

        Long userId = getCurrentUserId();

        Allergy allergy = allergyRepository.findById(allergyId)
                .orElseThrow(() -> new ResourceNotFoundException("Alergia no encontrada con ID: " + allergyId));

        allergyMapper.updateEntityFromDTO(updateDTO, allergy);
        allergy.setUpdatedBy(userId);

        Allergy updated = allergyRepository.save(allergy);
        log.info("Allergy updated successfully: {} by user: {}", allergyId, userId);

        return allergyMapper.toResponseDTO(updated);
    }

    @CacheEvict(value = "patientAllergies", key = "#result.patientId")
    public AllergyResponseDTO verify(Long allergyId) {
        log.info("Verifying allergy with ID: {}", allergyId);

        Long userId = getCurrentUserId();

        Allergy allergy = allergyRepository.findById(allergyId)
                .orElseThrow(() -> new ResourceNotFoundException("Alergia no encontrada con ID: " + allergyId));

        allergy.setVerified(true);
        allergy.setUpdatedBy(userId);

        Allergy verified = allergyRepository.save(allergy);
        log.info("Allergy verified successfully: {} by user: {}", allergyId, userId);

        return allergyMapper.toResponseDTO(verified);
    }

    @CacheEvict(value = "patientAllergies", key = "#result.patientId")
    public AllergyResponseDTO deactivate(Long allergyId) {
        log.info("Deactivating allergy with ID: {}", allergyId);

        Long userId = getCurrentUserId();

        Allergy allergy = allergyRepository.findById(allergyId)
                .orElseThrow(() -> new ResourceNotFoundException("Alergia no encontrada con ID: " + allergyId));

        allergy.setActive(false);
        allergy.setUpdatedBy(userId);

        Allergy deactivated = allergyRepository.save(allergy);
        log.info("Allergy deactivated successfully: {} by user: {}", allergyId, userId);

        return allergyMapper.toResponseDTO(deactivated);
    }

    public void delete(Long allergyId, Long patientId) {
        log.warn("Permanently deleting allergy with ID: {} by user: {}", allergyId, getCurrentUserId());

        Allergy allergy = allergyRepository.findById(allergyId)
                .orElseThrow(() -> new ResourceNotFoundException("Alergia no encontrada con ID: " + allergyId));

        if (!allergy.getPatient().getId().equals(patientId)) {
            throw new BusinessException("La alergia no pertenece al paciente especificado");
        }

        allergyRepository.delete(allergy);
        log.info("Allergy permanently deleted: {}", allergyId);
    }

    @Transactional(readOnly = true)
    public boolean hasCriticalAllergies(Long patientId) {
        return allergyRepository.hasCriticalAllergies(patientId);
    }

    @Transactional(readOnly = true)
    public long countActiveAllergies(Long patientId) {
        return allergyRepository.countByPatientIdAndActiveTrue(patientId);
    }
}
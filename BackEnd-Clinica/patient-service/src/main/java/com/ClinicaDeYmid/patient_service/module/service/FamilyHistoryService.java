package com.ClinicaDeYmid.patient_service.module.service;

import com.ClinicaDeYmid.patient_service.infra.exception.BusinessException;
import com.ClinicaDeYmid.patient_service.infra.exception.ResourceNotFoundException;
import com.ClinicaDeYmid.patient_service.infra.security.UserContextHolder;
import com.ClinicaDeYmid.patient_service.module.dto.family.FamilyHistoryRequestDTO;
import com.ClinicaDeYmid.patient_service.module.dto.family.FamilyHistoryResponseDTO;
import com.ClinicaDeYmid.patient_service.module.dto.family.FamilyHistorySummaryDTO;
import com.ClinicaDeYmid.patient_service.module.dto.family.FamilyHistoryUpdateDTO;
import com.ClinicaDeYmid.patient_service.module.entity.FamilyHistory;
import com.ClinicaDeYmid.patient_service.module.entity.Patient;
import com.ClinicaDeYmid.patient_service.module.mapper.FamilyHistoryMapper;
import com.ClinicaDeYmid.patient_service.module.repository.FamilyHistoryRepository;
import com.ClinicaDeYmid.patient_service.module.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FamilyHistoryService {

    private final FamilyHistoryRepository familyHistoryRepository;
    private final PatientRepository patientRepository;
    private final FamilyHistoryMapper familyHistoryMapper;

    private Long getCurrentUserId() {
        Long userId = UserContextHolder.getCurrentUserId();
        if (userId == null) {
            log.warn("No se pudo obtener userId del contexto de seguridad, usando fallback");
            return 1L;
        }
        return userId;
    }

    @CacheEvict(value = "patientFamilyHistory", key = "#patientId")
    public FamilyHistoryResponseDTO create(Long patientId, FamilyHistoryRequestDTO requestDTO) {
        log.info("Creating family history for patient: {}", patientId);

        Long userId = getCurrentUserId();

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado con ID: " + patientId));

        if (requestDTO.ageAtDeath() != null && requestDTO.ageOfOnset() != null
                && requestDTO.ageAtDeath() < requestDTO.ageOfOnset()) {
            throw new BusinessException("La edad al fallecer no puede ser menor que la edad de inicio de la condición");
        }

        FamilyHistory familyHistory = familyHistoryMapper.toEntity(requestDTO);
        familyHistory.setPatient(patient);
        familyHistory.setCreatedBy(userId);
        familyHistory.setUpdatedBy(userId);

        FamilyHistory saved = familyHistoryRepository.save(familyHistory);
        log.info("Family history created successfully with ID: {} by user: {}", saved.getId(), userId);

        return familyHistoryMapper.toResponseDTO(saved);
    }

    @Transactional(readOnly = true)
    public FamilyHistoryResponseDTO getById(Long historyId) {
        log.debug("Fetching family history with ID: {}", historyId);

        FamilyHistory history = familyHistoryRepository.findById(historyId)
                .orElseThrow(() -> new ResourceNotFoundException("Antecedente familiar no encontrado con ID: " + historyId));

        return familyHistoryMapper.toResponseDTO(history);
    }

    @Cacheable(value = "patientFamilyHistory", key = "#patientId")
    @Transactional(readOnly = true)
    public List<FamilyHistorySummaryDTO> getAllByPatientId(Long patientId) {
        log.debug("Fetching active family history for patient: {}", patientId);

        List<FamilyHistory> histories = familyHistoryRepository.findByPatientIdAndActiveTrue(patientId);
        return familyHistoryMapper.toSummaryDTOList(histories);
    }

    @Transactional(readOnly = true)
    public Page<FamilyHistoryResponseDTO> getAllByPatientIdPaginated(Long patientId, Pageable pageable) {
        log.debug("Fetching paginated family history for patient: {}", patientId);

        return familyHistoryRepository.findByPatientIdAndActiveTrue(patientId, pageable)
                .map(familyHistoryMapper::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public List<FamilyHistoryResponseDTO> getGeneticRiskHistories(Long patientId) {
        log.debug("Fetching family histories with genetic risk for patient: {}", patientId);

        return familyHistoryRepository.findGeneticRiskByPatientId(patientId)
                .stream()
                .map(familyHistoryMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FamilyHistoryResponseDTO> getScreeningRecommendedHistories(Long patientId) {
        log.debug("Fetching family histories requiring screening for patient: {}", patientId);

        return familyHistoryRepository.findScreeningRecommendedByPatientId(patientId)
                .stream()
                .map(familyHistoryMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FamilyHistoryResponseDTO> getUnverifiedHistories() {
        log.debug("Fetching unverified family histories");

        return familyHistoryRepository.findUnverifiedHistory()
                .stream()
                .map(familyHistoryMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FamilyHistoryResponseDTO> getByCondition(String conditionName) {
        log.debug("Fetching family histories by condition: {}", conditionName);

        return familyHistoryRepository.findByCondition(conditionName)
                .stream()
                .map(familyHistoryMapper::toResponseDTO)
                .toList();
    }

    @CacheEvict(value = "patientFamilyHistory", key = "#result.patientId")
    public FamilyHistoryResponseDTO update(Long historyId, FamilyHistoryUpdateDTO updateDTO) {
        log.info("Updating family history with ID: {}", historyId);

        Long userId = getCurrentUserId();

        FamilyHistory history = familyHistoryRepository.findById(historyId)
                .orElseThrow(() -> new ResourceNotFoundException("Antecedente familiar no encontrado con ID: " + historyId));

        familyHistoryMapper.updateEntityFromDTO(updateDTO, history);
        history.setUpdatedBy(userId);

        FamilyHistory updated = familyHistoryRepository.save(history);
        log.info("Family history updated successfully by user: {}", userId);

        return familyHistoryMapper.toResponseDTO(updated);
    }

    @CacheEvict(value = "patientFamilyHistory", key = "#result.patientId")
    public FamilyHistoryResponseDTO verify(Long historyId, String verifiedBy) {
        log.info("Verifying family history with ID: {}", historyId);

        Long userId = getCurrentUserId();

        FamilyHistory history = familyHistoryRepository.findById(historyId)
                .orElseThrow(() -> new ResourceNotFoundException("Antecedente familiar no encontrado con ID: " + historyId));

        if (Boolean.TRUE.equals(history.getVerified())) {
            throw new BusinessException("El antecedente familiar ya está verificado");
        }

        history.setVerified(true);
        history.setVerifiedBy(verifiedBy);
        history.setVerifiedDate(LocalDateTime.now());
        history.setUpdatedBy(userId);

        FamilyHistory verified = familyHistoryRepository.save(history);
        log.info("Family history verified successfully by user: {}", userId);

        return familyHistoryMapper.toResponseDTO(verified);
    }

    @CacheEvict(value = "patientFamilyHistory", key = "#result.patientId")
    public FamilyHistoryResponseDTO recommendScreening(Long historyId, String screeningDetails) {
        log.info("Recommending screening for family history ID: {}", historyId);

        Long userId = getCurrentUserId();

        FamilyHistory history = familyHistoryRepository.findById(historyId)
                .orElseThrow(() -> new ResourceNotFoundException("Antecedente familiar no encontrado con ID: " + historyId));

        history.setScreeningRecommended(true);
        history.setScreeningDetails(screeningDetails);
        history.setUpdatedBy(userId);

        FamilyHistory updated = familyHistoryRepository.save(history);
        log.info("Screening recommended successfully by user: {}", userId);

        return familyHistoryMapper.toResponseDTO(updated);
    }

    @CacheEvict(value = "patientFamilyHistory", key = "#result.patientId")
    public FamilyHistoryResponseDTO deactivate(Long historyId) {
        log.info("Deactivating family history with ID: {}", historyId);

        Long userId = getCurrentUserId();

        FamilyHistory history = familyHistoryRepository.findById(historyId)
                .orElseThrow(() -> new ResourceNotFoundException("Antecedente familiar no encontrado con ID: " + historyId));

        history.setActive(false);
        history.setUpdatedBy(userId);

        FamilyHistory deactivated = familyHistoryRepository.save(history);
        log.info("Family history deactivated successfully by user: {}", userId);

        return familyHistoryMapper.toResponseDTO(deactivated);
    }

    public void delete(Long historyId, Long patientId) {
        log.warn("Permanently deleting family history with ID: {} by user: {}", historyId, getCurrentUserId());

        FamilyHistory history = familyHistoryRepository.findById(historyId)
                .orElseThrow(() -> new ResourceNotFoundException("Antecedente familiar no encontrado con ID: " + historyId));

        if (!history.getPatient().getId().equals(patientId)) {
            throw new BusinessException("El antecedente no pertenece al paciente especificado");
        }

        familyHistoryRepository.delete(history);
        log.info("Family history permanently deleted");
    }

    @Transactional(readOnly = true)
    public boolean hasGeneticRisk(Long patientId) {
        return familyHistoryRepository.hasGeneticRisk(patientId);
    }

    @Transactional(readOnly = true)
    public long countActiveHistories(Long patientId) {
        return familyHistoryRepository.countByPatientIdAndActiveTrue(patientId);
    }
}
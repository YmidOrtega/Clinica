package com.ClinicaDeYmid.patient_service.module.service;

import com.ClinicaDeYmid.patient_service.infra.exception.BusinessException;
import com.ClinicaDeYmid.patient_service.infra.exception.ResourceNotFoundException;
import com.ClinicaDeYmid.patient_service.infra.security.UserContextHolder;
import com.ClinicaDeYmid.patient_service.module.dto.chronic.ChronicDiseaseRequestDTO;
import com.ClinicaDeYmid.patient_service.module.dto.chronic.ChronicDiseaseResponseDTO;
import com.ClinicaDeYmid.patient_service.module.dto.chronic.ChronicDiseaseSummaryDTO;
import com.ClinicaDeYmid.patient_service.module.dto.chronic.ChronicDiseaseUpdateDTO;
import com.ClinicaDeYmid.patient_service.module.entity.ChronicDisease;
import com.ClinicaDeYmid.patient_service.module.entity.Patient;
import com.ClinicaDeYmid.patient_service.module.enums.DiseaseSeverity;
import com.ClinicaDeYmid.patient_service.module.mapper.ChronicDiseaseMapper;
import com.ClinicaDeYmid.patient_service.module.repository.ChronicDiseaseRepository;
import com.ClinicaDeYmid.patient_service.module.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChronicDiseaseService {

    private final ChronicDiseaseRepository chronicDiseaseRepository;
    private final PatientRepository patientRepository;
    private final ChronicDiseaseMapper chronicDiseaseMapper;

    private Long getCurrentUserId() {
        Long userId = UserContextHolder.getCurrentUserId();
        if (userId == null) {
            log.warn("No se pudo obtener userId del contexto de seguridad, usando fallback");
            return 1L;
        }
        return userId;
    }

    @CacheEvict(value = "patientChronicDiseases", key = "#patientId")
    public ChronicDiseaseResponseDTO create(Long patientId, ChronicDiseaseRequestDTO requestDTO) {
        log.info("Creating chronic disease for patient: {}", patientId);

        Long userId = getCurrentUserId();

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado con ID: " + patientId));

        ChronicDisease disease = chronicDiseaseMapper.toEntity(requestDTO);
        disease.setPatient(patient);
        disease.setCreatedBy(userId);
        disease.setUpdatedBy(userId);

        ChronicDisease saved = chronicDiseaseRepository.save(disease);
        log.info("Chronic disease created successfully with ID: {} by user: {}", saved.getId(), userId);

        return chronicDiseaseMapper.toResponseDTO(saved);
    }

    @Transactional(readOnly = true)
    public ChronicDiseaseResponseDTO getById(Long diseaseId) {
        log.debug("Fetching chronic disease with ID: {}", diseaseId);

        ChronicDisease disease = chronicDiseaseRepository.findById(diseaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Enfermedad crónica no encontrada con ID: " + diseaseId));

        return chronicDiseaseMapper.toResponseDTO(disease);
    }

    @Cacheable(value = "patientChronicDiseases", key = "#patientId")
    @Transactional(readOnly = true)
    public List<ChronicDiseaseSummaryDTO> getAllByPatientId(Long patientId) {
        log.debug("Fetching active chronic diseases for patient: {}", patientId);

        List<ChronicDisease> diseases = chronicDiseaseRepository.findByPatientIdAndActiveTrue(patientId);
        return chronicDiseaseMapper.toSummaryDTOList(diseases);
    }

    @Transactional(readOnly = true)
    public Page<ChronicDiseaseResponseDTO> getAllByPatientIdPaginated(Long patientId, Pageable pageable) {
        log.debug("Fetching paginated chronic diseases for patient: {}", patientId);

        return chronicDiseaseRepository.findByPatientIdAndActiveTrue(patientId, pageable)
                .map(chronicDiseaseMapper::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public List<ChronicDiseaseResponseDTO> getCriticalDiseases(Long patientId) {
        log.debug("Fetching critical diseases for patient: {}", patientId);

        return chronicDiseaseRepository.findCriticalDiseasesByPatientId(patientId)
                .stream()
                .map(chronicDiseaseMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChronicDiseaseResponseDTO> getDiseasesRequiringSpecialist() {
        log.debug("Fetching diseases requiring specialist");

        return chronicDiseaseRepository.findDiseasesRequiringSpecialist()
                .stream()
                .map(chronicDiseaseMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChronicDiseaseResponseDTO> getRecentFlares(int daysAgo) {
        log.debug("Fetching diseases with flares in last {} days", daysAgo);

        LocalDate afterDate = LocalDate.now().minusDays(daysAgo);

        return chronicDiseaseRepository.findRecentFlares(afterDate)
                .stream()
                .map(chronicDiseaseMapper::toResponseDTO)
                .toList();
    }

    @CacheEvict(value = "patientChronicDiseases", key = "#result.patientId")
    public ChronicDiseaseResponseDTO update(Long diseaseId, ChronicDiseaseUpdateDTO updateDTO) {
        log.info("Updating chronic disease with ID: {}", diseaseId);

        Long userId = getCurrentUserId();

        ChronicDisease disease = chronicDiseaseRepository.findById(diseaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Enfermedad crónica no encontrada con ID: " + diseaseId));

        chronicDiseaseMapper.updateEntityFromDTO(updateDTO, disease);
        disease.setUpdatedBy(userId);

        ChronicDisease updated = chronicDiseaseRepository.save(disease);
        log.info("Chronic disease updated successfully: {} by user: {}", diseaseId, userId);

        return chronicDiseaseMapper.toResponseDTO(updated);
    }

    @CacheEvict(value = "patientChronicDiseases", key = "#result.patientId")
    public ChronicDiseaseResponseDTO updateSeverity(Long diseaseId, DiseaseSeverity severity) {
        log.info("Updating severity for disease ID: {} to {}", diseaseId, severity);

        Long userId = getCurrentUserId();

        ChronicDisease disease = chronicDiseaseRepository.findById(diseaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Enfermedad crónica no encontrada con ID: " + diseaseId));

        disease.setSeverity(severity);
        disease.setUpdatedBy(userId);

        ChronicDisease updated = chronicDiseaseRepository.save(disease);
        log.info("Disease severity updated successfully by user: {}", userId);

        return chronicDiseaseMapper.toResponseDTO(updated);
    }

    @CacheEvict(value = "patientChronicDiseases", key = "#result.patientId")
    public ChronicDiseaseResponseDTO registerFlare(Long diseaseId, LocalDate flareDate) {
        log.info("Registering flare for disease ID: {} on date: {}", diseaseId, flareDate);

        Long userId = getCurrentUserId();

        ChronicDisease disease = chronicDiseaseRepository.findById(diseaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Enfermedad crónica no encontrada con ID: " + diseaseId));

        disease.setLastFlareDate(flareDate);
        disease.setUpdatedBy(userId);

        ChronicDisease updated = chronicDiseaseRepository.save(disease);
        log.info("Flare registered successfully by user: {}", userId);

        return chronicDiseaseMapper.toResponseDTO(updated);
    }

    @CacheEvict(value = "patientChronicDiseases", key = "#result.patientId")
    public ChronicDiseaseResponseDTO deactivate(Long diseaseId) {
        log.info("Deactivating chronic disease with ID: {}", diseaseId);

        Long userId = getCurrentUserId();

        ChronicDisease disease = chronicDiseaseRepository.findById(diseaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Enfermedad crónica no encontrada con ID: " + diseaseId));

        disease.setActive(false);
        disease.setUpdatedBy(userId);

        ChronicDisease deactivated = chronicDiseaseRepository.save(disease);
        log.info("Chronic disease deactivated successfully by user: {}", userId);

        return chronicDiseaseMapper.toResponseDTO(deactivated);
    }

    public void delete(Long diseaseId, Long patientId) {
        log.warn("Permanently deleting chronic disease with ID: {} by user: {}", diseaseId, getCurrentUserId());

        ChronicDisease disease = chronicDiseaseRepository.findById(diseaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Enfermedad crónica no encontrada con ID: " + diseaseId));

        if (!disease.getPatient().getId().equals(patientId)) {
            throw new BusinessException("La enfermedad no pertenece al paciente especificado");
        }

        chronicDiseaseRepository.delete(disease);
        log.info("Chronic disease permanently deleted");
    }

    @Transactional(readOnly = true)
    public boolean hasCriticalDiseases(Long patientId) {
        return chronicDiseaseRepository.hasCriticalDiseases(patientId);
    }

    @Transactional(readOnly = true)
    public long countActiveDiseases(Long patientId) {
        return chronicDiseaseRepository.countByPatientIdAndActiveTrue(patientId);
    }
}
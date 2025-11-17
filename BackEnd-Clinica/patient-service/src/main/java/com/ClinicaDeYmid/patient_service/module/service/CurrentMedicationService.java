package com.ClinicaDeYmid.patient_service.module.service;

import com.ClinicaDeYmid.patient_service.infra.exception.BusinessException;
import com.ClinicaDeYmid.patient_service.infra.exception.ResourceNotFoundException;
import com.ClinicaDeYmid.patient_service.infra.security.UserContextHolder;
import com.ClinicaDeYmid.patient_service.module.dto.medication.CurrentMedicationRequestDTO;
import com.ClinicaDeYmid.patient_service.module.dto.medication.CurrentMedicationResponseDTO;
import com.ClinicaDeYmid.patient_service.module.dto.medication.CurrentMedicationUpdateDTO;
import com.ClinicaDeYmid.patient_service.module.dto.medication.MedicationSummaryDTO;
import com.ClinicaDeYmid.patient_service.module.entity.CurrentMedication;
import com.ClinicaDeYmid.patient_service.module.entity.Patient;
import com.ClinicaDeYmid.patient_service.module.mapper.CurrentMedicationMapper;
import com.ClinicaDeYmid.patient_service.module.repository.CurrentMedicationRepository;
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
public class CurrentMedicationService {

    private final CurrentMedicationRepository medicationRepository;
    private final PatientRepository patientRepository;
    private final CurrentMedicationMapper medicationMapper;

    private Long getCurrentUserId() {
        Long userId = UserContextHolder.getCurrentUserId();
        if (userId == null) {
            log.warn("No se pudo obtener userId del contexto de seguridad, usando fallback");
            return 1L;
        }
        return userId;
    }

    @CacheEvict(value = "patientMedications", key = "#patientId")
    public CurrentMedicationResponseDTO create(Long patientId, CurrentMedicationRequestDTO requestDTO) {
        log.info("Creating medication for patient: {}", patientId);

        Long userId = getCurrentUserId();

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado con ID: " + patientId));

        if (requestDTO.endDate() != null && requestDTO.endDate().isBefore(requestDTO.startDate())) {
            throw new BusinessException("La fecha de fin no puede ser anterior a la fecha de inicio");
        }

        CurrentMedication medication = medicationMapper.toEntity(requestDTO);
        medication.setPatient(patient);
        medication.setCreatedBy(userId);
        medication.setUpdatedBy(userId);

        CurrentMedication saved = medicationRepository.save(medication);
        log.info("Medication created successfully with ID: {} by user: {}", saved.getId(), userId);

        return medicationMapper.toResponseDTO(saved);
    }

    @Transactional(readOnly = true)
    public CurrentMedicationResponseDTO getById(Long medicationId) {
        log.debug("Fetching medication with ID: {}", medicationId);

        CurrentMedication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicamento no encontrado con ID: " + medicationId));

        return medicationMapper.toResponseDTO(medication);
    }

    @Cacheable(value = "patientMedications", key = "#patientId")
    @Transactional(readOnly = true)
    public List<MedicationSummaryDTO> getAllActiveByPatientId(Long patientId) {
        log.debug("Fetching active medications for patient: {}", patientId);

        List<CurrentMedication> medications = medicationRepository
                .findByPatientIdAndActiveTrueAndDiscontinuedFalse(patientId);

        return medicationMapper.toSummaryDTOList(medications);
    }

    @Transactional(readOnly = true)
    public Page<CurrentMedicationResponseDTO> getAllByPatientIdPaginated(Long patientId, Pageable pageable) {
        log.debug("Fetching paginated medications for patient: {}", patientId);

        return medicationRepository.findByPatientIdAndActiveTrueAndDiscontinuedFalse(patientId, pageable)
                .map(medicationMapper::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public List<CurrentMedicationResponseDTO> getExpiringMedications(int daysAhead) {
        log.debug("Fetching medications expiring in next {} days", daysAhead);

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(daysAhead);

        return medicationRepository.findExpiringMedications(startDate, endDate)
                .stream()
                .map(medicationMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CurrentMedicationResponseDTO> getMedicationsNeedingRefill(int threshold) {
        log.debug("Fetching medications needing refill (threshold: {})", threshold);

        return medicationRepository.findNeedingRefill(threshold)
                .stream()
                .map(medicationMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CurrentMedicationResponseDTO> getExpiredMedications() {
        log.debug("Fetching expired medications");

        return medicationRepository.findExpiredMedications(LocalDate.now())
                .stream()
                .map(medicationMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CurrentMedicationResponseDTO> getMedicationsWithInteractions(Long patientId) {
        log.debug("Fetching medications with interactions for patient: {}", patientId);

        return medicationRepository.findMedicationsWithInteractions(patientId)
                .stream()
                .map(medicationMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CurrentMedicationResponseDTO> getMedicationsWithSideEffects(Long patientId) {
        log.debug("Fetching medications with side effects for patient: {}", patientId);

        return medicationRepository.findMedicationsWithSideEffects(patientId)
                .stream()
                .map(medicationMapper::toResponseDTO)
                .toList();
    }

    @CacheEvict(value = "patientMedications", key = "#result.patientId")
    public CurrentMedicationResponseDTO update(Long medicationId, CurrentMedicationUpdateDTO updateDTO) {
        log.info("Updating medication with ID: {}", medicationId);

        Long userId = getCurrentUserId();

        CurrentMedication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicamento no encontrado con ID: " + medicationId));

        if (updateDTO.endDate() != null && updateDTO.startDate() != null
                && updateDTO.endDate().isBefore(updateDTO.startDate())) {
            throw new BusinessException("La fecha de fin no puede ser anterior a la fecha de inicio");
        }

        medicationMapper.updateEntityFromDTO(updateDTO, medication);
        medication.setUpdatedBy(userId);

        CurrentMedication updated = medicationRepository.save(medication);
        log.info("Medication updated successfully: {} by user: {}", medicationId, userId);

        return medicationMapper.toResponseDTO(updated);
    }

    @CacheEvict(value = "patientMedications", key = "#result.patientId")
    public CurrentMedicationResponseDTO discontinue(Long medicationId, String reason) {
        log.info("Discontinuing medication with ID: {}", medicationId);

        Long userId = getCurrentUserId();

        CurrentMedication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicamento no encontrado con ID: " + medicationId));

        if (Boolean.TRUE.equals(medication.getDiscontinued())) {
            throw new BusinessException("El medicamento ya está descontinuado");
        }

        medication.setDiscontinued(true);
        medication.setDiscontinuedDate(LocalDate.now());
        medication.setDiscontinuedReason(reason);
        medication.setActive(false);
        medication.setUpdatedBy(userId);

        CurrentMedication discontinued = medicationRepository.save(medication);
        log.info("Medication discontinued successfully by user: {}", userId);

        return medicationMapper.toResponseDTO(discontinued);
    }

    @CacheEvict(value = "patientMedications", key = "#result.patientId")
    public CurrentMedicationResponseDTO reactivate(Long medicationId) {
        log.info("Reactivating medication with ID: {}", medicationId);

        Long userId = getCurrentUserId();

        CurrentMedication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicamento no encontrado con ID: " + medicationId));

        if (!Boolean.TRUE.equals(medication.getDiscontinued())) {
            throw new BusinessException("El medicamento no está descontinuado");
        }

        medication.setDiscontinued(false);
        medication.setDiscontinuedDate(null);
        medication.setDiscontinuedReason(null);
        medication.setActive(true);
        medication.setUpdatedBy(userId);

        CurrentMedication reactivated = medicationRepository.save(medication);
        log.info("Medication reactivated successfully by user: {}", userId);

        return medicationMapper.toResponseDTO(reactivated);
    }

    @CacheEvict(value = "patientMedications", key = "#result.patientId")
    public CurrentMedicationResponseDTO registerRefill(Long medicationId, int refillsAdded) {
        log.info("Registering refill for medication ID: {} (adding {} refills)", medicationId, refillsAdded);

        Long userId = getCurrentUserId();

        CurrentMedication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicamento no encontrado con ID: " + medicationId));

        Integer currentRefills = medication.getRefillsRemaining() != null ? medication.getRefillsRemaining() : 0;
        medication.setRefillsRemaining(currentRefills + refillsAdded);
        medication.setUpdatedBy(userId);

        CurrentMedication updated = medicationRepository.save(medication);
        log.info("Refill registered successfully by user: {}", userId);

        return medicationMapper.toResponseDTO(updated);
    }

    @CacheEvict(value = "patientMedications", key = "#result.patientId")
    public CurrentMedicationResponseDTO deactivate(Long medicationId) {
        log.info("Deactivating medication with ID: {}", medicationId);

        Long userId = getCurrentUserId();

        CurrentMedication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicamento no encontrado con ID: " + medicationId));

        medication.setActive(false);
        medication.setUpdatedBy(userId);

        CurrentMedication deactivated = medicationRepository.save(medication);
        log.info("Medication deactivated successfully by user: {}", userId);

        return medicationMapper.toResponseDTO(deactivated);
    }

    public void delete(Long medicationId, Long patientId) {
        log.warn("Permanently deleting medication with ID: {} by user: {}", medicationId, getCurrentUserId());

        CurrentMedication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicamento no encontrado con ID: " + medicationId));

        if (!medication.getPatient().getId().equals(patientId)) {
            throw new BusinessException("El medicamento no pertenece al paciente especificado");
        }

        medicationRepository.delete(medication);
        log.info("Medication permanently deleted");
    }

    @Transactional(readOnly = true)
    public long countActiveMedications(Long patientId) {
        return medicationRepository.countByPatientIdAndActiveTrueAndDiscontinuedFalse(patientId);
    }
}
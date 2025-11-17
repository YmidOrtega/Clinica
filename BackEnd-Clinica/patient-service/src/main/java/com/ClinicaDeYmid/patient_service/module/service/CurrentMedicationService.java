package com.ClinicaDeYmid.patient_service.module.service;

import com.ClinicaDeYmid.patient_service.infra.exception.BusinessException;
import com.ClinicaDeYmid.patient_service.infra.exception.ResourceNotFoundException;
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

    /**
     * Crea un nuevo medicamento para un paciente
     */
    @CacheEvict(value = "patientMedications", key = "#patientId")
    public CurrentMedicationResponseDTO create(Long patientId, CurrentMedicationRequestDTO requestDTO, Long userId) {
        log.info("Creating medication for patient: {}", patientId);

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado con ID: " + patientId));

        // Validar fechas
        if (requestDTO.endDate() != null && requestDTO.endDate().isBefore(requestDTO.startDate())) {
            throw new BusinessException("La fecha de fin no puede ser anterior a la fecha de inicio");
        }

        CurrentMedication medication = medicationMapper.toEntity(requestDTO);
        medication.setPatient(patient);
        medication.setCreatedBy(userId);
        medication.setUpdatedBy(userId);

        CurrentMedication saved = medicationRepository.save(medication);
        log.info("Medication created successfully with ID: {}", saved.getId());

        return medicationMapper.toResponseDTO(saved);
    }

    /**
     * Obtiene un medicamento por ID
     */
    @Transactional(readOnly = true)
    public CurrentMedicationResponseDTO getById(Long medicationId) {
        log.debug("Fetching medication with ID: {}", medicationId);

        CurrentMedication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicamento no encontrado con ID: " + medicationId));

        return medicationMapper.toResponseDTO(medication);
    }

    /**
     * Obtiene todos los medicamentos activos de un paciente
     */
    @Cacheable(value = "patientMedications", key = "#patientId")
    @Transactional(readOnly = true)
    public List<MedicationSummaryDTO> getAllActiveByPatientId(Long patientId) {
        log.debug("Fetching active medications for patient: {}", patientId);

        List<CurrentMedication> medications = medicationRepository
                .findByPatientIdAndActiveTrueAndDiscontinuedFalse(patientId);

        return medicationMapper.toSummaryDTOList(medications);
    }

    /**
     * Obtiene medicamentos con paginación
     */
    @Transactional(readOnly = true)
    public Page<CurrentMedicationResponseDTO> getAllByPatientIdPaginated(Long patientId, Pageable pageable) {
        log.debug("Fetching paginated medications for patient: {}", patientId);

        return medicationRepository.findByPatientIdAndActiveTrueAndDiscontinuedFalse(patientId, pageable)
                .map(medicationMapper::toResponseDTO);
    }

    /**
     * Obtiene medicamentos que están próximos a vencer
     */
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

    /**
     * Obtiene medicamentos que necesitan resurtido
     */
    @Transactional(readOnly = true)
    public List<CurrentMedicationResponseDTO> getMedicationsNeedingRefill(int threshold) {
        log.debug("Fetching medications needing refill (threshold: {})", threshold);

        return medicationRepository.findNeedingRefill(threshold)
                .stream()
                .map(medicationMapper::toResponseDTO)
                .toList();
    }

    /**
     * Obtiene medicamentos vencidos
     */
    @Transactional(readOnly = true)
    public List<CurrentMedicationResponseDTO> getExpiredMedications() {
        log.debug("Fetching expired medications");

        return medicationRepository.findExpiredMedications(LocalDate.now())
                .stream()
                .map(medicationMapper::toResponseDTO)
                .toList();
    }

    /**
     * Obtiene medicamentos con interacciones
     */
    @Transactional(readOnly = true)
    public List<CurrentMedicationResponseDTO> getMedicationsWithInteractions(Long patientId) {
        log.debug("Fetching medications with interactions for patient: {}", patientId);

        return medicationRepository.findMedicationsWithInteractions(patientId)
                .stream()
                .map(medicationMapper::toResponseDTO)
                .toList();
    }

    /**
     * Obtiene medicamentos con efectos secundarios reportados
     */
    @Transactional(readOnly = true)
    public List<CurrentMedicationResponseDTO> getMedicationsWithSideEffects(Long patientId) {
        log.debug("Fetching medications with side effects for patient: {}", patientId);

        return medicationRepository.findMedicationsWithSideEffects(patientId)
                .stream()
                .map(medicationMapper::toResponseDTO)
                .toList();
    }

    /**
     * Actualiza un medicamento
     */
    @CacheEvict(value = "patientMedications", key = "#result.patientId")
    public CurrentMedicationResponseDTO update(Long medicationId, CurrentMedicationUpdateDTO updateDTO, Long userId) {
        log.info("Updating medication with ID: {}", medicationId);

        CurrentMedication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicamento no encontrado con ID: " + medicationId));

        // Validar fechas si se actualizan
        if (updateDTO.endDate() != null && updateDTO.startDate() != null
                && updateDTO.endDate().isBefore(updateDTO.startDate())) {
            throw new BusinessException("La fecha de fin no puede ser anterior a la fecha de inicio");
        }

        medicationMapper.updateEntityFromDTO(updateDTO, medication);
        medication.setUpdatedBy(userId);

        CurrentMedication updated = medicationRepository.save(medication);
        log.info("Medication updated successfully: {}", medicationId);

        return medicationMapper.toResponseDTO(updated);
    }

    /**
     * Descontinúa un medicamento
     */
    @CacheEvict(value = "patientMedications", key = "#result.patientId")
    public CurrentMedicationResponseDTO discontinue(Long medicationId, String reason, Long userId) {
        log.info("Discontinuing medication with ID: {}", medicationId);

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
        log.info("Medication discontinued successfully");

        return medicationMapper.toResponseDTO(discontinued);
    }

    /**
     * Reactiva un medicamento descontinuado
     */
    @CacheEvict(value = "patientMedications", key = "#result.patientId")
    public CurrentMedicationResponseDTO reactivate(Long medicationId, Long userId) {
        log.info("Reactivating medication with ID: {}", medicationId);

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
        log.info("Medication reactivated successfully");

        return medicationMapper.toResponseDTO(reactivated);
    }

    /**
     * Registra un resurtido
     */
    @CacheEvict(value = "patientMedications", key = "#result.patientId")
    public CurrentMedicationResponseDTO registerRefill(Long medicationId, int refillsAdded, Long userId) {
        log.info("Registering refill for medication ID: {} (adding {} refills)", medicationId, refillsAdded);

        CurrentMedication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicamento no encontrado con ID: " + medicationId));

        Integer currentRefills = medication.getRefillsRemaining() != null ? medication.getRefillsRemaining() : 0;
        medication.setRefillsRemaining(currentRefills + refillsAdded);
        medication.setUpdatedBy(userId);

        CurrentMedication updated = medicationRepository.save(medication);
        log.info("Refill registered successfully");

        return medicationMapper.toResponseDTO(updated);
    }

    /**
     * Desactiva un medicamento (soft delete)
     */
    @CacheEvict(value = "patientMedications", key = "#result.patientId")
    public CurrentMedicationResponseDTO deactivate(Long medicationId, Long userId) {
        log.info("Deactivating medication with ID: {}", medicationId);

        CurrentMedication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicamento no encontrado con ID: " + medicationId));

        medication.setActive(false);
        medication.setUpdatedBy(userId);

        CurrentMedication deactivated = medicationRepository.save(medication);
        log.info("Medication deactivated successfully");

        return medicationMapper.toResponseDTO(deactivated);
    }

    /**
     * Elimina permanentemente un medicamento
     */
    public void delete(Long medicationId, Long patientId) {
        log.warn("Permanently deleting medication with ID: {}", medicationId);

        CurrentMedication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicamento no encontrado con ID: " + medicationId));

        if (!medication.getPatient().getId().equals(patientId)) {
            throw new BusinessException("El medicamento no pertenece al paciente especificado");
        }

        medicationRepository.delete(medication);
        log.info("Medication permanently deleted");
    }

    /**
     * Cuenta medicamentos activos de un paciente
     */
    @Transactional(readOnly = true)
    public long countActiveMedications(Long patientId) {
        return medicationRepository.countByPatientIdAndActiveTrueAndDiscontinuedFalse(patientId);
    }
}
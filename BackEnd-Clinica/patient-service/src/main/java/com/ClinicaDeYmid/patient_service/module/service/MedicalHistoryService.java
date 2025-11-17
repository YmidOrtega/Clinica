package com.ClinicaDeYmid.patient_service.module.service;

import com.ClinicaDeYmid.patient_service.infra.exception.ResourceNotFoundException;
import com.ClinicaDeYmid.patient_service.module.dto.medical.MedicalHistoryRequestDTO;
import com.ClinicaDeYmid.patient_service.module.dto.medical.MedicalHistoryResponseDTO;
import com.ClinicaDeYmid.patient_service.module.dto.medical.MedicalHistoryUpdateDTO;
import com.ClinicaDeYmid.patient_service.module.entity.MedicalHistory;
import com.ClinicaDeYmid.patient_service.module.entity.Patient;
import com.ClinicaDeYmid.patient_service.module.mapper.MedicalHistoryMapper;
import com.ClinicaDeYmid.patient_service.module.repository.MedicalHistoryRepository;
import com.ClinicaDeYmid.patient_service.module.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MedicalHistoryService {

    private final MedicalHistoryRepository medicalHistoryRepository;
    private final PatientRepository patientRepository;
    private final MedicalHistoryMapper medicalHistoryMapper;

    /**
     * Crea o actualiza la historia médica de un paciente
     */
    @CachePut(value = "medicalHistory", key = "#patientId")
    public MedicalHistoryResponseDTO createOrUpdate(Long patientId, MedicalHistoryRequestDTO requestDTO, Long userId) {
        log.info("Creating/updating medical history for patient: {}", patientId);

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado con ID: " + patientId));

        MedicalHistory medicalHistory = medicalHistoryRepository.findByPatientId(patientId)
                .map(existing -> {
                    // Actualizar existente
                    medicalHistoryMapper.updateEntityFromDTO(
                            new MedicalHistoryUpdateDTO(
                                    requestDTO.bloodType(),
                                    requestDTO.rhFactor(),
                                    requestDTO.bloodPressure(),
                                    requestDTO.weight(),
                                    requestDTO.height(),
                                    requestDTO.smokingStatus(),
                                    requestDTO.alcoholConsumption(),
                                    requestDTO.exerciseFrequency(),
                                    requestDTO.dietType(),
                                    requestDTO.notes(),
                                    requestDTO.lastCheckupDate(),
                                    requestDTO.nextCheckupDate(),
                                    requestDTO.hasInsurance(),
                                    requestDTO.insuranceProvider(),
                                    requestDTO.insuranceNumber()
                            ),
                            existing
                    );
                    existing.setUpdatedBy(userId);
                    return existing;
                })
                .orElseGet(() -> {
                    // Crear nuevo
                    MedicalHistory newHistory = medicalHistoryMapper.toEntity(requestDTO);
                    newHistory.setPatient(patient);
                    newHistory.setCreatedBy(userId);
                    newHistory.setUpdatedBy(userId);
                    return newHistory;
                });

        MedicalHistory savedHistory = medicalHistoryRepository.save(medicalHistory);
        log.info("Medical history saved successfully for patient: {}", patientId);

        return medicalHistoryMapper.toResponseDTO(savedHistory);
    }

    /**
     * Obtiene la historia médica de un paciente por ID
     */
    @Cacheable(value = "medicalHistory", key = "#patientId")
    @Transactional(readOnly = true)
    public MedicalHistoryResponseDTO getByPatientId(Long patientId) {
        log.debug("Fetching medical history for patient: {}", patientId);

        MedicalHistory medicalHistory = medicalHistoryRepository.findByPatientId(patientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Historia médica no encontrada para el paciente con ID: " + patientId
                ));

        return medicalHistoryMapper.toResponseDTO(medicalHistory);
    }

    /**
     * Obtiene la historia médica por número de identificación del paciente
     */
    @Transactional(readOnly = true)
    public MedicalHistoryResponseDTO getByPatientIdentification(String identificationNumber) {
        log.debug("Fetching medical history for patient with identification: {}", identificationNumber);

        MedicalHistory medicalHistory = medicalHistoryRepository.findByPatientIdentificationNumber(identificationNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Historia médica no encontrada para el paciente con identificación: " + identificationNumber
                ));

        return medicalHistoryMapper.toResponseDTO(medicalHistory);
    }

    /**
     * Actualiza parcialmente la historia médica
     */
    @CachePut(value = "medicalHistory", key = "#patientId")
    public MedicalHistoryResponseDTO partialUpdate(Long patientId, MedicalHistoryUpdateDTO updateDTO, Long userId) {
        log.info("Partially updating medical history for patient: {}", patientId);

        MedicalHistory medicalHistory = medicalHistoryRepository.findByPatientId(patientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Historia médica no encontrada para el paciente con ID: " + patientId
                ));

        medicalHistoryMapper.updateEntityFromDTO(updateDTO, medicalHistory);
        medicalHistory.setUpdatedBy(userId);

        MedicalHistory updated = medicalHistoryRepository.save(medicalHistory);
        log.info("Medical history updated successfully for patient: {}", patientId);

        return medicalHistoryMapper.toResponseDTO(updated);
    }

    /**
     * Obtiene pacientes con chequeo próximo
     */
    @Transactional(readOnly = true)
    public List<MedicalHistoryResponseDTO> getUpcomingCheckups(int daysAhead) {
        log.debug("Fetching patients with checkup in next {} days", daysAhead);

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(daysAhead);

        return medicalHistoryRepository.findUpcomingCheckups(startDate, endDate)
                .stream()
                .map(medicalHistoryMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene pacientes que necesitan chequeo
     */
    @Transactional(readOnly = true)
    public List<MedicalHistoryResponseDTO> getPatientsNeedingCheckup(int monthsWithoutCheckup) {
        log.debug("Fetching patients needing checkup (without checkup for {} months)", monthsWithoutCheckup);

        LocalDate beforeDate = LocalDate.now().minusMonths(monthsWithoutCheckup);

        return medicalHistoryRepository.findPatientsNeedingCheckup(beforeDate)
                .stream()
                .map(medicalHistoryMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene pacientes con IMC fuera del rango saludable
     */
    @Transactional(readOnly = true)
    public List<MedicalHistoryResponseDTO> getPatientsWithUnhealthyBMI() {
        log.debug("Fetching patients with unhealthy BMI");

        double minHealthyBMI = 18.5;
        double maxHealthyBMI = 25.0;

        return medicalHistoryRepository.findByBmiOutOfRange(minHealthyBMI, maxHealthyBMI)
                .stream()
                .map(medicalHistoryMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene fumadores activos
     */
    @Transactional(readOnly = true)
    public List<MedicalHistoryResponseDTO> getSmokers() {
        log.debug("Fetching active smokers");

        return medicalHistoryRepository.findSmokers()
                .stream()
                .map(medicalHistoryMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Elimina la historia médica (no recomendado, solo para casos especiales)
     */
    @CacheEvict(value = "medicalHistory", key = "#patientId")
    public void delete(Long patientId) {
        log.warn("Deleting medical history for patient: {}", patientId);

        MedicalHistory medicalHistory = medicalHistoryRepository.findByPatientId(patientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Historia médica no encontrada para el paciente con ID: " + patientId
                ));

        medicalHistoryRepository.delete(medicalHistory);
        log.info("Medical history deleted for patient: {}", patientId);
    }

    /**
     * Verifica si existe historia médica para un paciente
     */
    @Transactional(readOnly = true)
    public boolean existsByPatientId(Long patientId) {
        return medicalHistoryRepository.existsByPatientId(patientId);
    }
}
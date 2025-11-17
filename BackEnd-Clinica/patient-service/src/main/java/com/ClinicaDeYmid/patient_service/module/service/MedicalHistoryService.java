package com.ClinicaDeYmid.patient_service.module.service;

import com.ClinicaDeYmid.patient_service.infra.exception.ResourceNotFoundException;
import com.ClinicaDeYmid.patient_service.infra.security.UserContextHolder;
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

    private Long getCurrentUserId() {
        Long userId = UserContextHolder.getCurrentUserId();
        if (userId == null) {
            log.warn("No se pudo obtener userId del contexto de seguridad, usando fallback");
            return 1L;
        }
        return userId;
    }

    @CachePut(value = "medicalHistory", key = "#patientId")
    public MedicalHistoryResponseDTO createOrUpdate(Long patientId, MedicalHistoryRequestDTO requestDTO) {
        log.info("Creating/updating medical history for patient: {}", patientId);

        Long userId = getCurrentUserId();

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado con ID: " + patientId));

        MedicalHistory medicalHistory = medicalHistoryRepository.findByPatientId(patientId)
                .map(existing -> {
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
                    MedicalHistory newHistory = medicalHistoryMapper.toEntity(requestDTO);
                    newHistory.setPatient(patient);
                    newHistory.setCreatedBy(userId);
                    newHistory.setUpdatedBy(userId);
                    return newHistory;
                });

        MedicalHistory savedHistory = medicalHistoryRepository.save(medicalHistory);
        log.info("Medical history saved successfully for patient: {} by user: {}", patientId, userId);

        return medicalHistoryMapper.toResponseDTO(savedHistory);
    }

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

    @Transactional(readOnly = true)
    public MedicalHistoryResponseDTO getByPatientIdentification(String identificationNumber) {
        log.debug("Fetching medical history for patient with identification: {}", identificationNumber);

        MedicalHistory medicalHistory = medicalHistoryRepository.findByPatientIdentificationNumber(identificationNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Historia médica no encontrada para el paciente con identificación: " + identificationNumber
                ));

        return medicalHistoryMapper.toResponseDTO(medicalHistory);
    }

    @CachePut(value = "medicalHistory", key = "#patientId")
    public MedicalHistoryResponseDTO partialUpdate(Long patientId, MedicalHistoryUpdateDTO updateDTO) {
        log.info("Partially updating medical history for patient: {}", patientId);

        Long userId = getCurrentUserId();

        MedicalHistory medicalHistory = medicalHistoryRepository.findByPatientId(patientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Historia médica no encontrada para el paciente con ID: " + patientId
                ));

        medicalHistoryMapper.updateEntityFromDTO(updateDTO, medicalHistory);
        medicalHistory.setUpdatedBy(userId);

        MedicalHistory updated = medicalHistoryRepository.save(medicalHistory);
        log.info("Medical history updated successfully for patient: {} by user: {}", patientId, userId);

        return medicalHistoryMapper.toResponseDTO(updated);
    }

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

    @Transactional(readOnly = true)
    public List<MedicalHistoryResponseDTO> getPatientsNeedingCheckup(int monthsWithoutCheckup) {
        log.debug("Fetching patients needing checkup (without checkup for {} months)", monthsWithoutCheckup);

        LocalDate beforeDate = LocalDate.now().minusMonths(monthsWithoutCheckup);

        return medicalHistoryRepository.findPatientsNeedingCheckup(beforeDate)
                .stream()
                .map(medicalHistoryMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

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

    @Transactional(readOnly = true)
    public List<MedicalHistoryResponseDTO> getSmokers() {
        log.debug("Fetching active smokers");

        return medicalHistoryRepository.findSmokers()
                .stream()
                .map(medicalHistoryMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @CacheEvict(value = "medicalHistory", key = "#patientId")
    public void delete(Long patientId) {
        log.warn("Deleting medical history for patient: {} by user: {}", patientId, getCurrentUserId());

        MedicalHistory medicalHistory = medicalHistoryRepository.findByPatientId(patientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Historia médica no encontrada para el paciente con ID: " + patientId
                ));

        medicalHistoryRepository.delete(medicalHistory);
        log.info("Medical history deleted for patient: {}", patientId);
    }

    @Transactional(readOnly = true)
    public boolean existsByPatientId(Long patientId) {
        return medicalHistoryRepository.existsByPatientId(patientId);
    }
}
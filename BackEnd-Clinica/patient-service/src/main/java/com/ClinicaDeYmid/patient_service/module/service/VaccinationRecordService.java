package com.ClinicaDeYmid.patient_service.module.service;

import com.ClinicaDeYmid.patient_service.infra.exception.BusinessException;
import com.ClinicaDeYmid.patient_service.infra.exception.ResourceNotFoundException;
import com.ClinicaDeYmid.patient_service.infra.security.UserContextHolder;
import com.ClinicaDeYmid.patient_service.module.dto.vaccination.VaccinationRecordRequestDTO;
import com.ClinicaDeYmid.patient_service.module.dto.vaccination.VaccinationRecordResponseDTO;
import com.ClinicaDeYmid.patient_service.module.dto.vaccination.VaccinationRecordUpdateDTO;
import com.ClinicaDeYmid.patient_service.module.dto.vaccination.VaccinationSummaryDTO;
import com.ClinicaDeYmid.patient_service.module.entity.Patient;
import com.ClinicaDeYmid.patient_service.module.entity.VaccinationRecord;
import com.ClinicaDeYmid.patient_service.module.mapper.VaccinationRecordMapper;
import com.ClinicaDeYmid.patient_service.module.repository.PatientRepository;
import com.ClinicaDeYmid.patient_service.module.repository.VaccinationRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class VaccinationRecordService {

    private final VaccinationRecordRepository vaccinationRepository;
    private final PatientRepository patientRepository;
    private final VaccinationRecordMapper vaccinationMapper;

    private Long getCurrentUserId() {
        Long userId = UserContextHolder.getCurrentUserId();
        if (userId == null) {
            log.warn("No se pudo obtener userId del contexto de seguridad, usando fallback");
            return 1L;
        }
        return userId;
    }

    @CacheEvict(value = "patientVaccinations", key = "#patientId")
    public VaccinationRecordResponseDTO create(Long patientId, VaccinationRecordRequestDTO requestDTO) {
        log.info("Creating vaccination record for patient: {}", patientId);

        Long userId = getCurrentUserId();

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado con ID: " + patientId));

        if (requestDTO.administeredDate().isAfter(LocalDate.now())) {
            throw new BusinessException("La fecha de administración no puede ser futura");
        }

        if (requestDTO.nextDoseDate() != null &&
                requestDTO.nextDoseDate().isBefore(requestDTO.administeredDate())) {
            throw new BusinessException("La fecha de próxima dosis no puede ser anterior a la fecha de administración");
        }

        if (requestDTO.expirationDate() != null &&
                requestDTO.expirationDate().isBefore(requestDTO.administeredDate())) {
            throw new BusinessException("La fecha de vencimiento no puede ser anterior a la fecha de administración");
        }

        if (requestDTO.totalDosesRequired() != null &&
                requestDTO.doseNumber() > requestDTO.totalDosesRequired()) {
            throw new BusinessException("El número de dosis no puede ser mayor que el total de dosis requeridas");
        }

        VaccinationRecord vaccination = vaccinationMapper.toEntity(requestDTO);
        vaccination.setPatient(patient);
        vaccination.setCreatedBy(userId);
        vaccination.setUpdatedBy(userId);

        VaccinationRecord saved = vaccinationRepository.save(vaccination);
        log.info("Vaccination record created successfully with ID: {} by user: {}", saved.getId(), userId);

        return vaccinationMapper.toResponseDTO(saved);
    }

    @Transactional(readOnly = true)
    public VaccinationRecordResponseDTO getById(Long vaccinationId) {
        log.debug("Fetching vaccination record with ID: {}", vaccinationId);

        VaccinationRecord vaccination = vaccinationRepository.findById(vaccinationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registro de vacunación no encontrado con ID: " + vaccinationId));

        return vaccinationMapper.toResponseDTO(vaccination);
    }

    @Cacheable(value = "patientVaccinations", key = "#patientId")
    @Transactional(readOnly = true)
    public List<VaccinationSummaryDTO> getAllByPatientId(Long patientId) {
        log.debug("Fetching vaccination records for patient: {}", patientId);

        List<VaccinationRecord> vaccinations = vaccinationRepository.findByPatientId(patientId);
        return vaccinationMapper.toSummaryDTOList(vaccinations);
    }

    @Transactional(readOnly = true)
    public Page<VaccinationRecordResponseDTO> getAllByPatientIdPaginated(Long patientId, Pageable pageable) {
        log.debug("Fetching paginated vaccination records for patient: {}", patientId);

        return vaccinationRepository.findByPatientId(patientId, pageable)
                .map(vaccinationMapper::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public List<VaccinationRecordResponseDTO> getUpcomingDoses(int daysAhead) {
        log.debug("Fetching upcoming doses in next {} days", daysAhead);

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(daysAhead);

        return vaccinationRepository.findUpcomingDoses(startDate, endDate)
                .stream()
                .map(vaccinationMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VaccinationRecordResponseDTO> getOverdueDoses() {
        log.debug("Fetching overdue doses");

        return vaccinationRepository.findOverdueDoses(LocalDate.now())
                .stream()
                .map(vaccinationMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VaccinationRecordResponseDTO> getIncompleteSchemes(Long patientId) {
        log.debug("Fetching incomplete vaccination schemes for patient: {}", patientId);

        return vaccinationRepository.findIncompleteSchemes(patientId)
                .stream()
                .map(vaccinationMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VaccinationRecordResponseDTO> getCompletedSchemes(Long patientId) {
        log.debug("Fetching completed vaccination schemes for patient: {}", patientId);

        return vaccinationRepository.findCompletedSchemes(patientId)
                .stream()
                .map(vaccinationMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VaccinationRecordResponseDTO> getVaccinesWithReactions(Long patientId) {
        log.debug("Fetching vaccines with adverse reactions for patient: {}", patientId);

        return vaccinationRepository.findByPatientIdAndHadReactionTrue(patientId)
                .stream()
                .map(vaccinationMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VaccinationRecordResponseDTO> getTravelValidVaccines(Long patientId) {
        log.debug("Fetching travel-valid vaccines for patient: {}", patientId);

        return vaccinationRepository.findTravelValidVaccines(patientId)
                .stream()
                .map(vaccinationMapper::toResponseDTO)
                .toList();
    }

    @CacheEvict(value = "patientVaccinations", key = "#result.patientId")
    public VaccinationRecordResponseDTO update(Long vaccinationId, VaccinationRecordUpdateDTO updateDTO) {
        log.info("Updating vaccination record with ID: {}", vaccinationId);

        Long userId = getCurrentUserId();

        VaccinationRecord vaccination = vaccinationRepository.findById(vaccinationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registro de vacunación no encontrado con ID: " + vaccinationId));

        vaccinationMapper.updateEntityFromDTO(updateDTO, vaccination);
        vaccination.setUpdatedBy(userId);

        VaccinationRecord updated = vaccinationRepository.save(vaccination);
        log.info("Vaccination record updated successfully by user: {}", userId);

        return vaccinationMapper.toResponseDTO(updated);
    }

    @CacheEvict(value = "patientVaccinations", key = "#result.patientId")
    public VaccinationRecordResponseDTO verify(Long vaccinationId, String verifiedBy) {
        log.info("Verifying vaccination record with ID: {}", vaccinationId);

        Long userId = getCurrentUserId();

        VaccinationRecord vaccination = vaccinationRepository.findById(vaccinationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registro de vacunación no encontrado con ID: " + vaccinationId));

        if (Boolean.TRUE.equals(vaccination.getVerified())) {
            throw new BusinessException("El registro de vacunación ya está verificado");
        }

        vaccination.setVerified(true);
        vaccination.setVerifiedBy(verifiedBy);
        vaccination.setVerifiedDate(LocalDateTime.now());
        vaccination.setUpdatedBy(userId);

        VaccinationRecord verified = vaccinationRepository.save(vaccination);
        log.info("Vaccination record verified successfully by user: {}", userId);

        return vaccinationMapper.toResponseDTO(verified);
    }

    @CacheEvict(value = "patientVaccinations", key = "#result.patientId")
    public VaccinationRecordResponseDTO registerReaction(
            Long vaccinationId,
            String reactions,
            String severity) {
        log.info("Registering adverse reaction for vaccination ID: {}", vaccinationId);

        Long userId = getCurrentUserId();

        VaccinationRecord vaccination = vaccinationRepository.findById(vaccinationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registro de vacunación no encontrado con ID: " + vaccinationId));

        vaccination.setHadReaction(true);
        vaccination.setAdverseReactions(reactions);
        vaccination.setReactionSeverity(severity);
        vaccination.setUpdatedBy(userId);

        VaccinationRecord updated = vaccinationRepository.save(vaccination);
        log.info("Adverse reaction registered successfully by user: {}", userId);

        return vaccinationMapper.toResponseDTO(updated);
    }

    public void delete(Long vaccinationId, Long patientId) {
        log.warn("Permanently deleting vaccination record with ID: {} by user: {}", vaccinationId, getCurrentUserId());

        VaccinationRecord vaccination = vaccinationRepository.findById(vaccinationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registro de vacunación no encontrado con ID: " + vaccinationId));

        if (!vaccination.getPatient().getId().equals(patientId)) {
            throw new BusinessException("El registro de vacunación no pertenece al paciente especificado");
        }

        vaccinationRepository.delete(vaccination);
        log.info("Vaccination record permanently deleted");
    }

    @Transactional(readOnly = true)
    public boolean hasCompletedVaccineScheme(Long patientId, String vaccineName) {
        return vaccinationRepository.hasCompletedVaccineScheme(patientId, vaccineName);
    }

    @Transactional(readOnly = true)
    public long countVaccinationRecords(Long patientId) {
        return vaccinationRepository.countByPatientId(patientId);
    }
}
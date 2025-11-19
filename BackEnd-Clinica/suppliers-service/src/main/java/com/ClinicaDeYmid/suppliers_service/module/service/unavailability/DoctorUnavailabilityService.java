package com.ClinicaDeYmid.suppliers_service.module.service.unavailability;

import com.ClinicaDeYmid.suppliers_service.module.dto.unavailability.DoctorUnavailabilityCreateDTO;
import com.ClinicaDeYmid.suppliers_service.module.dto.unavailability.DoctorUnavailabilityResponseDTO;
import com.ClinicaDeYmid.suppliers_service.module.dto.unavailability.DoctorUnavailabilityUpdateDTO;
import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import com.ClinicaDeYmid.suppliers_service.module.entity.DoctorUnavailability;
import com.ClinicaDeYmid.suppliers_service.module.mapper.DoctorUnavailabilityMapper;
import com.ClinicaDeYmid.suppliers_service.module.repository.DoctorRepository;
import com.ClinicaDeYmid.suppliers_service.module.repository.DoctorUnavailabilityRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorUnavailabilityService {

    private final DoctorUnavailabilityRepository unavailabilityRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorUnavailabilityMapper unavailabilityMapper;

    /**
     * Crea una nueva solicitud de ausencia para un doctor
     */
    @Transactional
    public DoctorUnavailabilityResponseDTO createUnavailability(DoctorUnavailabilityCreateDTO dto) {
        log.info("Creating unavailability for doctor ID: {} from {} to {}",
                dto.doctorId(), dto.startDate(), dto.endDate());

        // 1. Validar que el doctor existe y está activo
        Doctor doctor = doctorRepository.findById(dto.doctorId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Doctor no encontrado con ID: " + dto.doctorId()));

        if (!doctor.getActive()) {
            throw new IllegalStateException(
                    "No se pueden crear ausencias para un doctor inactivo");
        }

        // 2. Validar que no existe conflicto de fechas
        boolean hasConflict = unavailabilityRepository.existsDateConflict(
                dto.doctorId(),
                dto.startDate(),
                dto.endDate(),
                null
        );

        if (hasConflict) {
            throw new IllegalStateException(
                    String.format("Ya existe una ausencia que se solapa con el periodo %s - %s",
                            dto.startDate(), dto.endDate()));
        }

        // 3. Crear y guardar usando mapper
        DoctorUnavailability unavailability = unavailabilityMapper.toEntity(dto);
        unavailability.setDoctor(doctor);

        DoctorUnavailability saved = unavailabilityRepository.save(unavailability);

        log.info("Unavailability created successfully with ID: {}", saved.getId());

        return unavailabilityMapper.toResponseDTO(saved);
    }

    /**
     * Actualiza una ausencia existente (solo si no está aprobada)
     */
    @Transactional
    public DoctorUnavailabilityResponseDTO updateUnavailability(
            Long unavailabilityId, DoctorUnavailabilityUpdateDTO dto) {
        log.info("Updating unavailability ID: {}", unavailabilityId);

        DoctorUnavailability unavailability = unavailabilityRepository.findById(unavailabilityId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Ausencia no encontrada con ID: " + unavailabilityId));

        if (unavailability.getApproved()) {
            throw new IllegalStateException(
                    "No se puede actualizar una ausencia ya aprobada. " +
                            "Debe revocar la aprobación primero.");
        }

        // Actualizar usando mapper
        unavailabilityMapper.updateEntityFromDTO(dto, unavailability);

        // Validar conflictos solo si cambiaron las fechas
        if (dto.startDate() != null || dto.endDate() != null) {
            boolean hasConflict = unavailabilityRepository.existsDateConflict(
                    unavailability.getDoctor().getId(),
                    unavailability.getStartDate(),
                    unavailability.getEndDate(),
                    unavailabilityId
            );

            if (hasConflict) {
                throw new IllegalStateException(
                        "Las fechas actualizadas generan un conflicto con ausencias existentes");
            }
        }

        DoctorUnavailability updated = unavailabilityRepository.save(unavailability);
        log.info("Unavailability updated successfully: {}", unavailabilityId);

        return unavailabilityMapper.toResponseDTO(updated);
    }

    /**
     * Aprueba una ausencia
     */
    @Transactional
    public DoctorUnavailabilityResponseDTO approveUnavailability(
            Long unavailabilityId, String approvedBy) {
        log.info("Approving unavailability ID: {} by {}", unavailabilityId, approvedBy);

        DoctorUnavailability unavailability = unavailabilityRepository.findById(unavailabilityId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Ausencia no encontrada con ID: " + unavailabilityId));

        if (unavailability.getApproved()) {
            throw new IllegalStateException("La ausencia ya está aprobada");
        }

        unavailability.approve(approvedBy);
        DoctorUnavailability saved = unavailabilityRepository.save(unavailability);

        log.info("Unavailability approved successfully: {}", unavailabilityId);

        return unavailabilityMapper.toResponseDTO(saved);
    }

    /**
     * Revoca la aprobación de una ausencia
     */
    @Transactional
    public DoctorUnavailabilityResponseDTO revokeApproval(Long unavailabilityId) {
        log.info("Revoking approval for unavailability ID: {}", unavailabilityId);

        DoctorUnavailability unavailability = unavailabilityRepository.findById(unavailabilityId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Ausencia no encontrada con ID: " + unavailabilityId));

        if (!unavailability.getApproved()) {
            throw new IllegalStateException("La ausencia no está aprobada");
        }

        unavailability.revokeApproval();
        DoctorUnavailability saved = unavailabilityRepository.save(unavailability);

        log.info("Approval revoked successfully for: {}", unavailabilityId);

        return unavailabilityMapper.toResponseDTO(saved);
    }

    /**
     * Obtiene todas las ausencias de un doctor
     */
    @Transactional(readOnly = true)
    public List<DoctorUnavailabilityResponseDTO> getDoctorUnavailabilities(Long doctorId) {
        log.debug("Getting unavailabilities for doctor ID: {}", doctorId);

        if (!doctorRepository.existsById(doctorId)) {
            throw new EntityNotFoundException("Doctor no encontrado con ID: " + doctorId);
        }

        List<DoctorUnavailability> unavailabilities =
                unavailabilityRepository.findByDoctorId(doctorId);

        return unavailabilityMapper.toResponseDTOList(unavailabilities);
    }

    /**
     * Obtiene ausencias pendientes de aprobación de un doctor
     */
    @Transactional(readOnly = true)
    public List<DoctorUnavailabilityResponseDTO> getPendingUnavailabilities(Long doctorId) {
        log.debug("Getting pending unavailabilities for doctor ID: {}", doctorId);

        List<DoctorUnavailability> unavailabilities =
                unavailabilityRepository.findPendingByDoctorId(doctorId);

        return unavailabilityMapper.toResponseDTOList(unavailabilities);
    }

    /**
     * Obtiene ausencias futuras de un doctor
     */
    @Transactional(readOnly = true)
    public List<DoctorUnavailabilityResponseDTO> getFutureUnavailabilities(Long doctorId) {
        log.debug("Getting future unavailabilities for doctor ID: {}", doctorId);

        List<DoctorUnavailability> unavailabilities =
                unavailabilityRepository.findFutureUnavailabilities(doctorId, LocalDate.now());

        return unavailabilityMapper.toResponseDTOList(unavailabilities);
    }

    /**
     * Verifica si un doctor está ausente en una fecha específica
     */
    @Transactional(readOnly = true)
    public boolean isDoctorUnavailable(Long doctorId, LocalDate date) {
        return unavailabilityRepository.isDoctorUnavailableOn(doctorId, date);
    }

    /**
     * Elimina una ausencia (solo si no está aprobada)
     */
    @Transactional
    public void deleteUnavailability(Long unavailabilityId) {
        log.info("Deleting unavailability ID: {}", unavailabilityId);

        DoctorUnavailability unavailability = unavailabilityRepository.findById(unavailabilityId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Ausencia no encontrada con ID: " + unavailabilityId));

        if (unavailability.getApproved()) {
            throw new IllegalStateException(
                    "No se puede eliminar una ausencia aprobada. " +
                            "Debe revocar la aprobación primero.");
        }

        unavailabilityRepository.delete(unavailability);
        log.info("Unavailability deleted successfully: {}", unavailabilityId);
    }
}
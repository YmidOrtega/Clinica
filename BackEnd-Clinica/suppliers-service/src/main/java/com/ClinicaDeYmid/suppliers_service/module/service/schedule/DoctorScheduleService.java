package com.ClinicaDeYmid.suppliers_service.module.service.schedule;

import com.ClinicaDeYmid.suppliers_service.module.dto.schedule.DoctorScheduleCreateDTO;
import com.ClinicaDeYmid.suppliers_service.module.dto.schedule.DoctorScheduleResponseDTO;
import com.ClinicaDeYmid.suppliers_service.module.dto.schedule.DoctorScheduleUpdateDTO;
import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import com.ClinicaDeYmid.suppliers_service.module.entity.DoctorSchedule;
import com.ClinicaDeYmid.suppliers_service.module.mapper.DoctorScheduleMapper;
import com.ClinicaDeYmid.suppliers_service.module.repository.DoctorRepository;
import com.ClinicaDeYmid.suppliers_service.module.repository.DoctorScheduleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorScheduleService {

    private final DoctorScheduleRepository scheduleRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorScheduleMapper scheduleMapper;

    /**
     * Crea un nuevo horario para un doctor
     */
    @Transactional
    public DoctorScheduleResponseDTO createSchedule(DoctorScheduleCreateDTO dto) {
        log.info("Creating schedule for doctor ID: {} on {}", dto.doctorId(), dto.dayOfWeek());

        // 1. Validar que el doctor existe y está activo
        Doctor doctor = doctorRepository.findById(dto.doctorId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Doctor no encontrado con ID: " + dto.doctorId()));

        if (!doctor.getActive()) {
            throw new IllegalStateException(
                    "No se pueden crear horarios para un doctor inactivo");
        }

        // 2. Validar que no existe conflicto de horarios
        boolean hasConflict = scheduleRepository.existsScheduleConflict(
                dto.doctorId(),
                dto.dayOfWeek(),
                dto.startTime(),
                dto.endTime(),
                null
        );

        if (hasConflict) {
            throw new IllegalStateException(
                    String.format("Ya existe un horario que se solapa con %s de %s a %s",
                            dto.dayOfWeek(), dto.startTime(), dto.endTime()));
        }

        // 3. Crear y guardar usando mapper
        DoctorSchedule schedule = scheduleMapper.toEntity(dto);
        schedule.setDoctor(doctor);

        DoctorSchedule saved = scheduleRepository.save(schedule);

        log.info("Schedule created successfully with ID: {}", saved.getId());

        return scheduleMapper.toResponseDTO(saved);
    }

    /**
     * Actualiza un horario existente
     */
    @Transactional
    public DoctorScheduleResponseDTO updateSchedule(Long scheduleId, DoctorScheduleUpdateDTO dto) {
        log.info("Updating schedule ID: {}", scheduleId);

        DoctorSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Horario no encontrado con ID: " + scheduleId));

        // Actualizar usando mapper
        scheduleMapper.updateEntityFromDTO(dto, schedule);

        // Validar conflictos solo si cambió el día o las horas
        if (dto.dayOfWeek() != null || dto.startTime() != null || dto.endTime() != null) {
            boolean hasConflict = scheduleRepository.existsScheduleConflict(
                    schedule.getDoctor().getId(),
                    schedule.getDayOfWeek(),
                    schedule.getStartTime(),
                    schedule.getEndTime(),
                    scheduleId
            );

            if (hasConflict) {
                throw new IllegalStateException(
                        "El horario actualizado genera un conflicto con horarios existentes");
            }
        }

        DoctorSchedule updated = scheduleRepository.save(schedule);
        log.info("Schedule updated successfully: {}", scheduleId);

        return scheduleMapper.toResponseDTO(updated);
    }

    /**
     * Obtiene todos los horarios activos de un doctor
     */
    @Transactional(readOnly = true)
    public List<DoctorScheduleResponseDTO> getDoctorSchedules(Long doctorId) {
        log.debug("Getting schedules for doctor ID: {}", doctorId);

        if (!doctorRepository.existsById(doctorId)) {
            throw new EntityNotFoundException("Doctor no encontrado con ID: " + doctorId);
        }

        List<DoctorSchedule> schedules = scheduleRepository.findActiveSchedulesByDoctorId(doctorId);

        return scheduleMapper.toResponseDTOList(schedules);
    }

    /**
     * Obtiene horarios de un doctor para un día específico
     */
    @Transactional(readOnly = true)
    public List<DoctorScheduleResponseDTO> getDoctorSchedulesByDay(Long doctorId, DayOfWeek dayOfWeek) {
        log.debug("Getting schedules for doctor ID: {} on {}", doctorId, dayOfWeek);

        List<DoctorSchedule> schedules = scheduleRepository.findByDoctorIdAndDayOfWeek(
                doctorId, dayOfWeek);

        return scheduleMapper.toResponseDTOList(schedules);
    }

    /**
     * Encuentra IDs de doctores disponibles en un día y hora específicos
     */
    @Transactional(readOnly = true)
    public List<Long> findAvailableDoctorIds(DayOfWeek dayOfWeek, LocalTime time) {
        log.debug("Finding available doctors on {} at {}", dayOfWeek, time);

        return scheduleRepository.findAvailableDoctorIdsAt(dayOfWeek, time);
    }

    /**
     * Elimina un horario (soft delete)
     */
    @Transactional
    public void deactivateSchedule(Long scheduleId) {
        log.info("Deactivating schedule ID: {}", scheduleId);

        DoctorSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Horario no encontrado con ID: " + scheduleId));

        schedule.setActive(false);
        scheduleRepository.save(schedule);

        log.info("Schedule deactivated successfully: {}", scheduleId);
    }

    /**
     * Elimina permanentemente un horario
     */
    @Transactional
    public void deleteSchedule(Long scheduleId) {
        log.info("Deleting schedule ID: {}", scheduleId);

        if (!scheduleRepository.existsById(scheduleId)) {
            throw new EntityNotFoundException("Horario no encontrado con ID: " + scheduleId);
        }

        scheduleRepository.deleteById(scheduleId);
        log.info("Schedule deleted successfully: {}", scheduleId);
    }

    /**
     * Elimina todos los horarios de un doctor
     */
    @Transactional
    public void deleteAllDoctorSchedules(Long doctorId) {
        log.info("Deleting all schedules for doctor ID: {}", doctorId);

        scheduleRepository.deleteByDoctorId(doctorId);
        log.info("All schedules deleted for doctor: {}", doctorId);
    }
}
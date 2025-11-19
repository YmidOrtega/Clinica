package com.ClinicaDeYmid.suppliers_service.module.repository;

import com.ClinicaDeYmid.suppliers_service.module.entity.DoctorSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, Long> {

    /**
     * Encuentra todos los horarios activos de un doctor
     */
    @Query("SELECT ds FROM DoctorSchedule ds WHERE ds.doctor.id = :doctorId AND ds.active = true")
    List<DoctorSchedule> findActiveSchedulesByDoctorId(@Param("doctorId") Long doctorId);

    /**
     * Encuentra horarios de un doctor para un día específico
     */
    @Query("SELECT ds FROM DoctorSchedule ds WHERE ds.doctor.id = :doctorId " +
            "AND ds.dayOfWeek = :dayOfWeek AND ds.active = true")
    List<DoctorSchedule> findByDoctorIdAndDayOfWeek(
            @Param("doctorId") Long doctorId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek
    );

    /**
     * Encuentra todos los doctores disponibles en un día y hora específicos
     */
    @Query("""
        SELECT DISTINCT ds.doctor.id FROM DoctorSchedule ds
        WHERE ds.dayOfWeek = :dayOfWeek
        AND ds.active = true
        AND ds.startTime <= :time
        AND ds.endTime >= :time
        """)
    List<Long> findAvailableDoctorIdsAt(
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("time") LocalTime time
    );

    /**
     * Verifica si existe un conflicto de horario para un doctor
     */
    @Query("""
        SELECT COUNT(ds) > 0 FROM DoctorSchedule ds
        WHERE ds.doctor.id = :doctorId
        AND ds.dayOfWeek = :dayOfWeek
        AND ds.active = true
        AND (
            (ds.startTime <= :startTime AND ds.endTime > :startTime) OR
            (ds.startTime < :endTime AND ds.endTime >= :endTime) OR
            (ds.startTime >= :startTime AND ds.endTime <= :endTime)
        )
        AND (:scheduleId IS NULL OR ds.id != :scheduleId)
        """)
    boolean existsScheduleConflict(
            @Param("doctorId") Long doctorId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("scheduleId") Long scheduleId
    );

    /**
     * Encuentra todos los horarios de doctores por especialidad
     */
    @Query("""
        SELECT ds FROM DoctorSchedule ds
        JOIN ds.doctor d
        JOIN d.specialties s
        WHERE s.id = :specialtyId
        AND ds.active = true
        AND d.active = true
        """)
    List<DoctorSchedule> findSchedulesBySpecialtyId(@Param("specialtyId") Long specialtyId);

    /**
     * Cuenta los horarios activos de un doctor
     */
    @Query("SELECT COUNT(ds) FROM DoctorSchedule ds WHERE ds.doctor.id = :doctorId AND ds.active = true")
    long countActiveSchedulesByDoctorId(@Param("doctorId") Long doctorId);

    /**
     * Elimina todos los horarios de un doctor
     */
    void deleteByDoctorId(Long doctorId);
}
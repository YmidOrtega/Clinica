package com.ClinicaDeYmid.suppliers_service.module.repository;

import com.ClinicaDeYmid.suppliers_service.module.entity.DoctorUnavailability;
import com.ClinicaDeYmid.suppliers_service.module.enums.UnavailabilityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DoctorUnavailabilityRepository extends JpaRepository<DoctorUnavailability, Long> {

    /**
     * Encuentra todas las ausencias de un doctor
     */
    @Query("SELECT du FROM DoctorUnavailability du WHERE du.doctor.id = :doctorId ORDER BY du.startDate DESC")
    List<DoctorUnavailability> findByDoctorId(@Param("doctorId") Long doctorId);

    /**
     * Encuentra ausencias aprobadas de un doctor
     */
    @Query("SELECT du FROM DoctorUnavailability du " +
            "WHERE du.doctor.id = :doctorId AND du.approved = true " +
            "ORDER BY du.startDate DESC")
    List<DoctorUnavailability> findApprovedByDoctorId(@Param("doctorId") Long doctorId);

    /**
     * Encuentra ausencias pendientes de aprobación de un doctor
     */
    @Query("SELECT du FROM DoctorUnavailability du " +
            "WHERE du.doctor.id = :doctorId AND du.approved = false " +
            "ORDER BY du.startDate")
    List<DoctorUnavailability> findPendingByDoctorId(@Param("doctorId") Long doctorId);

    /**
     * Encuentra ausencias activas de un doctor en una fecha específica
     */
    @Query("""
        SELECT du FROM DoctorUnavailability du
        WHERE du.doctor.id = :doctorId
        AND du.approved = true
        AND :date BETWEEN du.startDate AND du.endDate
        """)
    List<DoctorUnavailability> findActiveUnavailabilitiesOn(
            @Param("doctorId") Long doctorId,
            @Param("date") LocalDate date
    );

    /**
     * Verifica si un doctor está ausente en una fecha específica
     */
    @Query("""
        SELECT COUNT(du) > 0 FROM DoctorUnavailability du
        WHERE du.doctor.id = :doctorId
        AND du.approved = true
        AND :date BETWEEN du.startDate AND du.endDate
        """)
    boolean isDoctorUnavailableOn(
            @Param("doctorId") Long doctorId,
            @Param("date") LocalDate date
    );

    /**
     * Encuentra ausencias que se superponen con un periodo específico
     */
    @Query("""
        SELECT du FROM DoctorUnavailability du
        WHERE du.doctor.id = :doctorId
        AND du.approved = true
        AND (
            (du.startDate <= :endDate AND du.endDate >= :startDate)
        )
        """)
    List<DoctorUnavailability> findOverlappingUnavailabilities(
            @Param("doctorId") Long doctorId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Verifica si existe un conflicto de fechas para ausencias
     */
    @Query("""
        SELECT COUNT(du) > 0 FROM DoctorUnavailability du
        WHERE du.doctor.id = :doctorId
        AND (
            (du.startDate <= :endDate AND du.endDate >= :startDate)
        )
        AND (:unavailabilityId IS NULL OR du.id != :unavailabilityId)
        """)
    boolean existsDateConflict(
            @Param("doctorId") Long doctorId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("unavailabilityId") Long unavailabilityId
    );

    /**
     * Encuentra ausencias por tipo
     */
    @Query("SELECT du FROM DoctorUnavailability du WHERE du.type = :type ORDER BY du.startDate DESC")
    List<DoctorUnavailability> findByType(@Param("type") UnavailabilityType type);

    /**
     * Encuentra ausencias futuras de un doctor
     */
    @Query("""
        SELECT du FROM DoctorUnavailability du
        WHERE du.doctor.id = :doctorId
        AND du.approved = true
        AND du.endDate >= :currentDate
        ORDER BY du.startDate
        """)
    List<DoctorUnavailability> findFutureUnavailabilities(
            @Param("doctorId") Long doctorId,
            @Param("currentDate") LocalDate currentDate
    );

    /**
     * Encuentra IDs de doctores que NO están ausentes en una fecha específica
     */
    @Query("""
        SELECT d.id FROM Doctor d
        WHERE d.active = true
        AND d.id NOT IN (
            SELECT du.doctor.id FROM DoctorUnavailability du
            WHERE du.approved = true
            AND :date BETWEEN du.startDate AND du.endDate
        )
        """)
    List<Long> findAvailableDoctorIdsOn(@Param("date") LocalDate date);

    /**
     * Cuenta ausencias aprobadas de un doctor
     */
    @Query("SELECT COUNT(du) FROM DoctorUnavailability du " +
            "WHERE du.doctor.id = :doctorId AND du.approved = true")
    long countApprovedByDoctorId(@Param("doctorId") Long doctorId);

    /**
     * Elimina todas las ausencias de un doctor
     */
    void deleteByDoctorId(Long doctorId);
}
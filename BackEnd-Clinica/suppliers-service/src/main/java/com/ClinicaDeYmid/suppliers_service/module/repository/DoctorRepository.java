package com.ClinicaDeYmid.suppliers_service.module.repository;

import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    @Query("""
        SELECT DISTINCT d FROM Doctor d
        LEFT JOIN FETCH d.specialties s
        LEFT JOIN FETCH s.subSpecialties ss
        LEFT JOIN FETCH d.subSpecialties ds
        WHERE d.id = :id
        """)
    Optional<Doctor> findByIdWithSpecialtiesAndSubSpecialties(@Param("id") Long id);

    @Query("""
        SELECT DISTINCT d FROM Doctor d
        LEFT JOIN FETCH d.specialties s
        LEFT JOIN FETCH s.subSpecialties ss
        LEFT JOIN FETCH d.subSpecialties ds
        WHERE d.active = true
        """)
    List<Doctor> findAllActiveWithSpecialtiesAndSubSpecialties();

    /**
     * Verifica si existe un doctor con un email específico (excluyendo un ID)
     */
    @Query("SELECT COUNT(d) > 0 FROM Doctor d WHERE d.email = :email AND (:doctorId IS NULL OR d.id != :doctorId)")
    boolean existsByEmail(@Param("email") String email, @Param("doctorId") Long doctorId);

    /**
     * Verifica si existe un doctor con una licencia médica específica (excluyendo un ID)
     */
    @Query("SELECT COUNT(d) > 0 FROM Doctor d WHERE d.licenseNumber = :licenseNumber AND (:doctorId IS NULL OR d.id != :doctorId)")
    boolean existsByLicenseNumber(@Param("licenseNumber") String licenseNumber, @Param("doctorId") Long doctorId);

    /**
     * Verifica si existe un doctor con un código de proveedor específico (excluyendo un ID)
     */
    @Query("SELECT COUNT(d) > 0 FROM Doctor d WHERE d.providerCode = :providerCode AND (:doctorId IS NULL OR d.id != :doctorId)")
    boolean existsByProviderCode(@Param("providerCode") Integer providerCode, @Param("doctorId") Long doctorId);

    /**
     * Verifica si existe un doctor con un número de identificación específico (excluyendo un ID)
     */
    @Query("SELECT COUNT(d) > 0 FROM Doctor d WHERE d.identificationNumber = :identificationNumber AND (:doctorId IS NULL OR d.id != :doctorId)")
    boolean existsByIdentificationNumber(@Param("identificationNumber") String identificationNumber, @Param("doctorId") Long doctorId);

    /**
     * Encuentra doctores activos por especialidad con paginación
     */
    @Query("""
        SELECT DISTINCT d FROM Doctor d
        JOIN d.specialties s
        WHERE s.id = :specialtyId
        AND d.active = true
        """)
    Page<Doctor> findBySpecialtyId(@Param("specialtyId") Long specialtyId, Pageable pageable);

    /**
     * Encuentra doctores activos por especialidad (lista completa)
     */
    @Query("""
        SELECT DISTINCT d FROM Doctor d
        LEFT JOIN FETCH d.specialties s
        LEFT JOIN FETCH d.subSpecialties ds
        WHERE s.id = :specialtyId
        AND d.active = true
        """)
    List<Doctor> findAllBySpecialtyId(@Param("specialtyId") Long specialtyId);

    /**
     * Encuentra doctores activos por múltiples especialidades
     */
    @Query("""
        SELECT DISTINCT d FROM Doctor d
        JOIN d.specialties s
        WHERE s.id IN :specialtyIds
        AND d.active = true
        """)
    List<Doctor> findBySpecialtyIds(@Param("specialtyIds") List<Long> specialtyIds);

    /**
     * Encuentra doctores activos por subespecialidad con paginación
     */
    @Query("""
        SELECT DISTINCT d FROM Doctor d
        JOIN d.subSpecialties ss
        WHERE ss.id = :subSpecialtyId
        AND d.active = true
        """)
    Page<Doctor> findBySubSpecialtyId(@Param("subSpecialtyId") Long subSpecialtyId, Pageable pageable);

    /**
     * Encuentra doctores activos por subespecialidad (lista completa)
     */
    @Query("""
        SELECT DISTINCT d FROM Doctor d
        LEFT JOIN FETCH d.specialties s
        LEFT JOIN FETCH d.subSpecialties ss
        WHERE ss.id = :subSpecialtyId
        AND d.active = true
        """)
    List<Doctor> findAllBySubSpecialtyId(@Param("subSpecialtyId") Long subSpecialtyId);

    /**
     * Encuentra doctores activos por nombre o apellido (búsqueda parcial)
     */
    @Query("""
        SELECT d FROM Doctor d
        WHERE d.active = true
        AND (LOWER(d.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
             OR LOWER(d.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
        """)
    Page<Doctor> searchByNameOrLastName(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Encuentra doctores activos por nombre completo (búsqueda exacta)
     */
    @Query("""
        SELECT d FROM Doctor d
        WHERE d.active = true
        AND (LOWER(CONCAT(d.name, ' ', d.lastName)) = LOWER(:fullName)
             OR LOWER(CONCAT(d.lastName, ' ', d.name)) = LOWER(:fullName))
        """)
    List<Doctor> findByFullName(@Param("fullName") String fullName);

    /**
     * Encuentra doctores disponibles en un día y hora específicos
     */
    @Query("""
        SELECT DISTINCT d FROM Doctor d
        JOIN DoctorSchedule ds ON ds.doctor.id = d.id
        LEFT JOIN DoctorUnavailability du ON du.doctor.id = d.id
        WHERE d.active = true
        AND ds.active = true
        AND ds.dayOfWeek = :dayOfWeek
        AND ds.startTime <= :time
        AND ds.endTime >= :time
        AND (du.id IS NULL OR NOT (
            du.approved = true
            AND :date BETWEEN du.startDate AND du.endDate
        ))
        """)
    List<Doctor> findAvailableDoctorsAt(
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("time") LocalTime time,
            @Param("date") LocalDate date
    );

    /**
     * Encuentra doctores disponibles en un día y hora específicos para una especialidad
     */
    @Query("""
        SELECT DISTINCT d FROM Doctor d
        JOIN d.specialties s
        JOIN DoctorSchedule ds ON ds.doctor.id = d.id
        LEFT JOIN DoctorUnavailability du ON du.doctor.id = d.id
        WHERE d.active = true
        AND s.id = :specialtyId
        AND ds.active = true
        AND ds.dayOfWeek = :dayOfWeek
        AND ds.startTime <= :time
        AND ds.endTime >= :time
        AND (du.id IS NULL OR NOT (
            du.approved = true
            AND :date BETWEEN du.startDate AND du.endDate
        ))
        """)
    List<Doctor> findAvailableDoctorsBySpecialtyAt(
            @Param("specialtyId") Long specialtyId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("time") LocalTime time,
            @Param("date") LocalDate date
    );

    /**
     * Encuentra doctores que tienen horarios configurados
     */
    @Query("""
        SELECT DISTINCT d FROM Doctor d
        JOIN DoctorSchedule ds ON ds.doctor.id = d.id
        WHERE d.active = true
        AND ds.active = true
        """)
    List<Doctor> findDoctorsWithSchedules();

    /**
     * Búsqueda avanzada con múltiples filtros opcionales
     */
    @Query("""
        SELECT DISTINCT d FROM Doctor d
        LEFT JOIN d.specialties s
        LEFT JOIN d.subSpecialties ss
        WHERE d.active = true
        AND (:specialtyId IS NULL OR s.id = :specialtyId)
        AND (:subSpecialtyId IS NULL OR ss.id = :subSpecialtyId)
        AND (:searchTerm IS NULL OR
             LOWER(d.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR
             LOWER(d.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
        """)
    Page<Doctor> searchDoctorsWithFilters(
            @Param("specialtyId") Long specialtyId,
            @Param("subSpecialtyId") Long subSpecialtyId,
            @Param("searchTerm") String searchTerm,
            Pageable pageable
    );

    /**
     * Cuenta doctores activos por especialidad
     */
    @Query("SELECT COUNT(DISTINCT d) FROM Doctor d JOIN d.specialties s WHERE s.id = :specialtyId AND d.active = true")
    long countBySpecialtyId(@Param("specialtyId") Long specialtyId);

    /**
     * Cuenta doctores activos por subespecialidad
     */
    @Query("SELECT COUNT(DISTINCT d) FROM Doctor d JOIN d.subSpecialties ss WHERE ss.id = :subSpecialtyId AND d.active = true")
    long countBySubSpecialtyId(@Param("subSpecialtyId") Long subSpecialtyId);

    /**
     * Cuenta doctores activos totales
     */
    @Query("SELECT COUNT(d) FROM Doctor d WHERE d.active = true")
    long countActiveDoctors();

    /**
     * Encuentra doctores activos sin horarios configurados
     */
    @Query("""
        SELECT d FROM Doctor d
        WHERE d.active = true
        AND d.id NOT IN (SELECT DISTINCT ds.doctor.id FROM DoctorSchedule ds WHERE ds.active = true)
        """)
    List<Doctor> findDoctorsWithoutSchedules();

    /**
     * Encuentra doctor incluyendo eliminados (bypasea @SQLRestriction)
     */
    @Query(value = "SELECT * FROM doctors WHERE id = :id", nativeQuery = true)
    Optional<Doctor> findByIdIncludingDeleted(@Param("id") Long id);
}
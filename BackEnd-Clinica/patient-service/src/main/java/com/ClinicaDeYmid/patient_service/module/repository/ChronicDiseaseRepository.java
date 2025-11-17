package com.ClinicaDeYmid.patient_service.module.repository;

import com.ClinicaDeYmid.patient_service.module.entity.ChronicDisease;
import com.ClinicaDeYmid.patient_service.module.enums.DiseaseSeverity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ChronicDiseaseRepository extends JpaRepository<ChronicDisease, Long> {

    /**
     * Busca todas las enfermedades crónicas de un paciente
     */
    List<ChronicDisease> findByPatientId(Long patientId);

    /**
     * Busca enfermedades crónicas activas de un paciente
     */
    List<ChronicDisease> findByPatientIdAndActiveTrue(Long patientId);

    /**
     * Busca enfermedades crónicas activas de un paciente (paginado)
     */
    Page<ChronicDisease> findByPatientIdAndActiveTrue(Long patientId, Pageable pageable);

    /**
     * Busca enfermedades por severidad
     */
    List<ChronicDisease> findBySeverityAndActiveTrue(DiseaseSeverity severity);

    /**
     * Busca enfermedades críticas o no controladas
     */
    @Query("""
        SELECT cd FROM ChronicDisease cd
        WHERE cd.active = true
        AND cd.severity IN ('CRITICAL', 'UNCONTROLLED')
        ORDER BY cd.severity DESC, cd.lastFlareDate DESC
        """)
    List<ChronicDisease> findCriticalOrUncontrolledDiseases();

    /**
     * Busca enfermedades críticas de un paciente
     */
    @Query("""
        SELECT cd FROM ChronicDisease cd
        WHERE cd.patient.id = :patientId
        AND cd.active = true
        AND cd.severity IN ('CRITICAL', 'UNCONTROLLED')
        ORDER BY cd.severity DESC
        """)
    List<ChronicDisease> findCriticalDiseasesByPatientId(@Param("patientId") Long patientId);

    /**
     * Busca enfermedades por código CIE-10
     */
    List<ChronicDisease> findByIcd10CodeAndActiveTrue(String icd10Code);

    /**
     * Busca enfermedades que requieren especialista
     */
    @Query("""
        SELECT cd FROM ChronicDisease cd
        WHERE cd.active = true
        AND cd.requiresSpecialist = true
        """)
    List<ChronicDisease> findDiseasesRequiringSpecialist();

    /**
     * Busca enfermedades que requieren especialista por tipo
     */
    @Query("""
        SELECT cd FROM ChronicDisease cd
        WHERE cd.active = true
        AND cd.requiresSpecialist = true
        AND LOWER(cd.specialistType) LIKE LOWER(CONCAT('%', :specialistType, '%'))
        """)
    List<ChronicDisease> findBySpecialistType(@Param("specialistType") String specialistType);

    /**
     * Busca enfermedades por nombre
     */
    @Query("""
        SELECT cd FROM ChronicDisease cd
        WHERE cd.active = true
        AND LOWER(cd.diseaseName) LIKE LOWER(CONCAT('%', :diseaseName, '%'))
        """)
    List<ChronicDisease> findByDiseaseName(@Param("diseaseName") String diseaseName);

    /**
     * Busca enfermedades con brote reciente
     */
    @Query("""
        SELECT cd FROM ChronicDisease cd
        WHERE cd.active = true
        AND cd.lastFlareDate IS NOT NULL
        AND cd.lastFlareDate >= :afterDate
        ORDER BY cd.lastFlareDate DESC
        """)
    List<ChronicDisease> findRecentFlares(@Param("afterDate") LocalDate afterDate);

    /**
     * Verifica si un paciente tiene enfermedades críticas
     */
    @Query("""
        SELECT COUNT(cd) > 0 FROM ChronicDisease cd
        WHERE cd.patient.id = :patientId
        AND cd.active = true
        AND cd.severity = 'CRITICAL'
        """)
    boolean hasCriticalDiseases(@Param("patientId") Long patientId);

    /**
     * Cuenta enfermedades activas de un paciente
     */
    long countByPatientIdAndActiveTrue(Long patientId);

    /**
     * Busca pacientes con enfermedad específica
     */
    @Query("""
        SELECT DISTINCT cd.patient.id FROM ChronicDisease cd
        WHERE cd.active = true
        AND LOWER(cd.diseaseName) LIKE LOWER(CONCAT('%', :diseaseName, '%'))
        """)
    List<Long> findPatientIdsWithDisease(@Param("diseaseName") String diseaseName);

    /**
     * Estadísticas de enfermedades por severidad
     */
    @Query("""
        SELECT cd.severity, COUNT(cd)
        FROM ChronicDisease cd
        WHERE cd.active = true
        GROUP BY cd.severity
        """)
    List<Object[]> countBySeverity();

    /**
     * Enfermedades más comunes
     */
    @Query("""
        SELECT cd.diseaseName, COUNT(cd)
        FROM ChronicDisease cd
        WHERE cd.active = true
        GROUP BY cd.diseaseName
        ORDER BY COUNT(cd) DESC
        """)
    List<Object[]> findMostCommonDiseases(Pageable pageable);
}
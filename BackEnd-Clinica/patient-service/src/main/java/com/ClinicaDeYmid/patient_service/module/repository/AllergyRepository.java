package com.ClinicaDeYmid.patient_service.module.repository;

import com.ClinicaDeYmid.patient_service.module.entity.Allergy;
import com.ClinicaDeYmid.patient_service.module.enums.AllergySeverity;
import com.ClinicaDeYmid.patient_service.module.enums.AllergyReactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AllergyRepository extends JpaRepository<Allergy, Long> {

    /**
     * Busca todas las alergias de un paciente
     */
    List<Allergy> findByPatientId(Long patientId);

    /**
     * Busca alergias activas de un paciente
     */
    List<Allergy> findByPatientIdAndActiveTrue(Long patientId);

    /**
     * Busca alergias activas de un paciente (paginado)
     */
    Page<Allergy> findByPatientIdAndActiveTrue(Long patientId, Pageable pageable);

    /**
     * Busca alergias por severidad
     */
    List<Allergy> findBySeverityAndActiveTrue(AllergySeverity severity);

    /**
     * Busca alergias críticas de un paciente (SEVERE o LIFE_THREATENING)
     */
    @Query("""
        SELECT a FROM Allergy a
        WHERE a.patient.id = :patientId
        AND a.active = true
        AND a.severity IN ('SEVERE', 'LIFE_THREATENING')
        ORDER BY a.severity DESC
        """)
    List<Allergy> findCriticalAllergiesByPatientId(@Param("patientId") Long patientId);

    /**
     * Busca alergias por tipo de reacción
     */
    List<Allergy> findByReactionTypeAndActiveTrue(AllergyReactionType reactionType);

    /**
     * Busca alergias por alérgeno específico
     */
    @Query("""
        SELECT a FROM Allergy a
        WHERE a.active = true
        AND LOWER(a.allergen) LIKE LOWER(CONCAT('%', :allergen, '%'))
        """)
    List<Allergy> findByAllergen(@Param("allergen") String allergen);

    /**
     * Busca alergias no verificadas
     */
    @Query("""
        SELECT a FROM Allergy a
        WHERE a.active = true
        AND a.verified = false
        ORDER BY a.severity DESC, a.createdAt ASC
        """)
    List<Allergy> findUnverifiedAllergies();

    /**
     * Busca alergias no verificadas de un paciente
     */
    List<Allergy> findByPatientIdAndActiveTrueAndVerifiedFalse(Long patientId);

    /**
     * Verifica si un paciente tiene alergias críticas activas
     */
    @Query("""
        SELECT COUNT(a) > 0 FROM Allergy a
        WHERE a.patient.id = :patientId
        AND a.active = true
        AND a.severity IN ('SEVERE', 'LIFE_THREATENING')
        """)
    boolean hasCriticalAllergies(@Param("patientId") Long patientId);

    /**
     * Cuenta alergias activas de un paciente
     */
    long countByPatientIdAndActiveTrue(Long patientId);

    /**
     * Busca pacientes con alergia específica
     */
    @Query("""
        SELECT DISTINCT a.patient.id FROM Allergy a
        WHERE a.active = true
        AND LOWER(a.allergen) = LOWER(:allergen)
        """)
    List<Long> findPatientIdsWithAllergen(@Param("allergen") String allergen);

    /**
     * Busca alergias por tipo de reacción y severidad
     */
    @Query("""
        SELECT a FROM Allergy a
        WHERE a.active = true
        AND a.reactionType = :reactionType
        AND a.severity = :severity
        """)
    List<Allergy> findByReactionTypeAndSeverity(
            @Param("reactionType") AllergyReactionType reactionType,
            @Param("severity") AllergySeverity severity
    );

    /**
     * Estadísticas de alergias por severidad
     */
    @Query("""
        SELECT a.severity, COUNT(a)
        FROM Allergy a
        WHERE a.active = true
        GROUP BY a.severity
        """)
    List<Object[]> countBySeverity();
}
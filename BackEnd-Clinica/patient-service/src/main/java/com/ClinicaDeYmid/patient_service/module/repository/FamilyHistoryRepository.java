package com.ClinicaDeYmid.patient_service.module.repository;

import com.ClinicaDeYmid.patient_service.module.entity.FamilyHistory;
import com.ClinicaDeYmid.patient_service.module.enums.FamilyRelationship;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FamilyHistoryRepository extends JpaRepository<FamilyHistory, Long> {

    /**
     * Busca todos los antecedentes familiares de un paciente
     */
    List<FamilyHistory> findByPatientId(Long patientId);

    /**
     * Busca antecedentes activos de un paciente
     */
    List<FamilyHistory> findByPatientIdAndActiveTrue(Long patientId);

    /**
     * Busca antecedentes activos de un paciente (paginado)
     */
    Page<FamilyHistory> findByPatientIdAndActiveTrue(Long patientId, Pageable pageable);

    /**
     * Busca antecedentes por relación familiar
     */
    List<FamilyHistory> findByRelationshipAndActiveTrue(FamilyRelationship relationship);

    /**
     * Busca antecedentes por condición
     */
    @Query("""
        SELECT fh FROM FamilyHistory fh
        WHERE fh.active = true
        AND LOWER(fh.conditionName) LIKE LOWER(CONCAT('%', :conditionName, '%'))
        """)
    List<FamilyHistory> findByCondition(@Param("conditionName") String conditionName);

    /**
     * Busca antecedentes con riesgo genético
     */
    @Query("""
        SELECT fh FROM FamilyHistory fh
        WHERE fh.active = true
        AND fh.geneticRisk = true
        """)
    List<FamilyHistory> findWithGeneticRisk();

    /**
     * Busca antecedentes con riesgo genético de un paciente
     */
    @Query("""
        SELECT fh FROM FamilyHistory fh
        WHERE fh.patient.id = :patientId
        AND fh.active = true
        AND fh.geneticRisk = true
        """)
    List<FamilyHistory> findGeneticRiskByPatientId(@Param("patientId") Long patientId);

    /**
     * Busca antecedentes que requieren screening
     */
    @Query("""
        SELECT fh FROM FamilyHistory fh
        WHERE fh.active = true
        AND fh.screeningRecommended = true
        """)
    List<FamilyHistory> findRequiringScreening();

    /**
     * Busca antecedentes que requieren screening de un paciente
     */
    @Query("""
        SELECT fh FROM FamilyHistory fh
        WHERE fh.patient.id = :patientId
        AND fh.active = true
        AND fh.screeningRecommended = true
        """)
    List<FamilyHistory> findScreeningRecommendedByPatientId(@Param("patientId") Long patientId);

    /**
     * Busca antecedentes no verificados
     */
    @Query("""
        SELECT fh FROM FamilyHistory fh
        WHERE fh.active = true
        AND fh.verified = false
        ORDER BY fh.createdAt ASC
        """)
    List<FamilyHistory> findUnverifiedHistory();

    /**
     * Busca antecedentes por código CIE-10
     */
    List<FamilyHistory> findByIcd10CodeAndActiveTrue(String icd10Code);

    /**
     * Verifica si un paciente tiene antecedentes con riesgo genético
     */
    @Query("""
        SELECT COUNT(fh) > 0 FROM FamilyHistory fh
        WHERE fh.patient.id = :patientId
        AND fh.active = true
        AND fh.geneticRisk = true
        """)
    boolean hasGeneticRisk(@Param("patientId") Long patientId);

    /**
     * Cuenta antecedentes activos de un paciente
     */
    long countByPatientIdAndActiveTrue(Long patientId);

    /**
     * Busca pacientes con antecedente familiar específico
     */
    @Query("""
        SELECT DISTINCT fh.patient.id FROM FamilyHistory fh
        WHERE fh.active = true
        AND LOWER(fh.conditionName) LIKE LOWER(CONCAT('%', :conditionName, '%'))
        """)
    List<Long> findPatientIdsWithFamilyCondition(@Param("conditionName") String conditionName);

    /**
     * Condiciones familiares más comunes
     */
    @Query("""
        SELECT fh.conditionName, COUNT(fh)
        FROM FamilyHistory fh
        WHERE fh.active = true
        GROUP BY fh.conditionName
        ORDER BY COUNT(fh) DESC
        """)
    List<Object[]> findMostCommonFamilyConditions(Pageable pageable);

    /**
     * Busca antecedentes por relación y condición
     */
    @Query("""
        SELECT fh FROM FamilyHistory fh
        WHERE fh.active = true
        AND fh.relationship = :relationship
        AND LOWER(fh.conditionName) LIKE LOWER(CONCAT('%', :conditionName, '%'))
        """)
    List<FamilyHistory> findByRelationshipAndCondition(
            @Param("relationship") FamilyRelationship relationship,
            @Param("conditionName") String conditionName
    );
}
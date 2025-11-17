package com.ClinicaDeYmid.patient_service.module.repository;

import com.ClinicaDeYmid.patient_service.module.entity.CurrentMedication;
import com.ClinicaDeYmid.patient_service.module.enums.MedicationRoute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CurrentMedicationRepository extends JpaRepository<CurrentMedication, Long> {

    /**
     * Busca todos los medicamentos de un paciente
     */
    List<CurrentMedication> findByPatientId(Long patientId);

    /**
     * Busca medicamentos activos de un paciente
     */
    List<CurrentMedication> findByPatientIdAndActiveTrueAndDiscontinuedFalse(Long patientId);

    /**
     * Busca medicamentos activos de un paciente (paginado)
     */
    Page<CurrentMedication> findByPatientIdAndActiveTrueAndDiscontinuedFalse(Long patientId, Pageable pageable);

    /**
     * Busca medicamentos por nombre
     */
    @Query("""
        SELECT cm FROM CurrentMedication cm
        WHERE cm.active = true
        AND cm.discontinued = false
        AND (LOWER(cm.medicationName) LIKE LOWER(CONCAT('%', :name, '%'))
             OR LOWER(cm.genericName) LIKE LOWER(CONCAT('%', :name, '%')))
        """)
    List<CurrentMedication> findByMedicationName(@Param("name") String name);

    /**
     * Busca medicamentos por vía de administración
     */
    List<CurrentMedication> findByRouteAndActiveTrueAndDiscontinuedFalse(MedicationRoute route);

    /**
     * Busca medicamentos prescritos por un médico específico
     */
    @Query("""
        SELECT cm FROM CurrentMedication cm
        WHERE cm.active = true
        AND cm.discontinued = false
        AND cm.prescribedById = :doctorId
        """)
    List<CurrentMedication> findByPrescribedBy(@Param("doctorId") Long doctorId);

    /**
     * Busca medicamentos que están próximos a vencer
     */
    @Query("""
        SELECT cm FROM CurrentMedication cm
        WHERE cm.active = true
        AND cm.discontinued = false
        AND cm.endDate IS NOT NULL
        AND cm.endDate BETWEEN :startDate AND :endDate
        ORDER BY cm.endDate ASC
        """)
    List<CurrentMedication> findExpiringMedications(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Busca medicamentos que necesitan resurtido
     */
    @Query("""
        SELECT cm FROM CurrentMedication cm
        WHERE cm.active = true
        AND cm.discontinued = false
        AND (cm.refillsRemaining IS NULL OR cm.refillsRemaining <= :threshold)
        """)
    List<CurrentMedication> findNeedingRefill(@Param("threshold") Integer threshold);

    /**
     * Busca medicamentos vencidos
     */
    @Query("""
        SELECT cm FROM CurrentMedication cm
        WHERE cm.active = true
        AND cm.discontinued = false
        AND cm.endDate IS NOT NULL
        AND cm.endDate < :currentDate
        """)
    List<CurrentMedication> findExpiredMedications(@Param("currentDate") LocalDate currentDate);

    /**
     * Busca medicamentos descontinuados de un paciente
     */
    List<CurrentMedication> findByPatientIdAndDiscontinuedTrue(Long patientId);

    /**
     * Busca medicamentos por razón de prescripción
     */
    @Query("""
        SELECT cm FROM CurrentMedication cm
        WHERE cm.active = true
        AND cm.discontinued = false
        AND LOWER(cm.reason) LIKE LOWER(CONCAT('%', :reason, '%'))
        """)
    List<CurrentMedication> findByReason(@Param("reason") String reason);

    /**
     * Cuenta medicamentos activos de un paciente
     */
    long countByPatientIdAndActiveTrueAndDiscontinuedFalse(Long patientId);

    /**
     * Busca pacientes con medicamento específico
     */
    @Query("""
        SELECT DISTINCT cm.patient.id FROM CurrentMedication cm
        WHERE cm.active = true
        AND cm.discontinued = false
        AND LOWER(cm.medicationName) LIKE LOWER(CONCAT('%', :medication, '%'))
        """)
    List<Long> findPatientIdsWithMedication(@Param("medication") String medication);

    /**
     * Busca posibles interacciones medicamentosas
     */
    @Query("""
        SELECT cm FROM CurrentMedication cm
        WHERE cm.patient.id = :patientId
        AND cm.active = true
        AND cm.discontinued = false
        AND cm.interactions IS NOT NULL
        AND cm.interactions != ''
        """)
    List<CurrentMedication> findMedicationsWithInteractions(@Param("patientId") Long patientId);

    /**
     * Busca medicamentos con efectos secundarios reportados
     */
    @Query("""
        SELECT cm FROM CurrentMedication cm
        WHERE cm.patient.id = :patientId
        AND cm.active = true
        AND cm.discontinued = false
        AND cm.sideEffects IS NOT NULL
        AND cm.sideEffects != ''
        """)
    List<CurrentMedication> findMedicationsWithSideEffects(@Param("patientId") Long patientId);

    /**
     * Medicamentos más prescritos
     */
    @Query("""
        SELECT cm.medicationName, COUNT(cm)
        FROM CurrentMedication cm
        WHERE cm.active = true
        AND cm.discontinued = false
        GROUP BY cm.medicationName
        ORDER BY COUNT(cm) DESC
        """)
    List<Object[]> findMostPrescribedMedications(Pageable pageable);
}
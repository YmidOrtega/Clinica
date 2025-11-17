package com.ClinicaDeYmid.patient_service.module.repository;

import com.ClinicaDeYmid.patient_service.module.entity.VaccinationRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface VaccinationRecordRepository extends JpaRepository<VaccinationRecord, Long> {

    /**
     * Busca todos los registros de vacunación de un paciente
     */
    List<VaccinationRecord> findByPatientId(Long patientId);

    /**
     * Busca registros de vacunación de un paciente (paginado)
     */
    Page<VaccinationRecord> findByPatientId(Long patientId, Pageable pageable);

    /**
     * Busca registros por nombre de vacuna
     */
    @Query("""
        SELECT vr FROM VaccinationRecord vr
        WHERE LOWER(vr.vaccineName) LIKE LOWER(CONCAT('%', :vaccineName, '%'))
        ORDER BY vr.administeredDate DESC
        """)
    List<VaccinationRecord> findByVaccineName(@Param("vaccineName") String vaccineName);

    /**
     * Busca registros de una vacuna específica para un paciente
     */
    @Query("""
        SELECT vr FROM VaccinationRecord vr
        WHERE vr.patient.id = :patientId
        AND LOWER(vr.vaccineName) LIKE LOWER(CONCAT('%', :vaccineName, '%'))
        ORDER BY vr.administeredDate DESC
        """)
    List<VaccinationRecord> findByPatientIdAndVaccineName(
            @Param("patientId") Long patientId,
            @Param("vaccineName") String vaccineName
    );

    /**
     * Busca vacunas próximas a vencer
     */
    @Query("""
        SELECT vr FROM VaccinationRecord vr
        WHERE vr.nextDoseDate IS NOT NULL
        AND vr.nextDoseDate BETWEEN :startDate AND :endDate
        ORDER BY vr.nextDoseDate ASC
        """)
    List<VaccinationRecord> findUpcomingDoses(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Busca dosis atrasadas
     */
    @Query("""
        SELECT vr FROM VaccinationRecord vr
        WHERE vr.nextDoseDate IS NOT NULL
        AND vr.nextDoseDate < :currentDate
        AND vr.doseNumber < vr.totalDosesRequired
        ORDER BY vr.nextDoseDate ASC
        """)
    List<VaccinationRecord> findOverdueDoses(@Param("currentDate") LocalDate currentDate);

    /**
     * Busca esquemas de vacunación incompletos
     */
    @Query("""
        SELECT vr FROM VaccinationRecord vr
        WHERE vr.patient.id = :patientId
        AND vr.totalDosesRequired IS NOT NULL
        AND vr.doseNumber < vr.totalDosesRequired
        ORDER BY vr.nextDoseDate ASC
        """)
    List<VaccinationRecord> findIncompleteSchemes(@Param("patientId") Long patientId);

    /**
     * Busca esquemas completados
     */
    @Query("""
        SELECT vr FROM VaccinationRecord vr
        WHERE vr.patient.id = :patientId
        AND vr.totalDosesRequired IS NOT NULL
        AND vr.doseNumber = vr.totalDosesRequired
        ORDER BY vr.administeredDate DESC
        """)
    List<VaccinationRecord> findCompletedSchemes(@Param("patientId") Long patientId);

    /**
     * Busca vacunas con reacciones adversas
     */
    @Query("""
        SELECT vr FROM VaccinationRecord vr
        WHERE vr.hadReaction = true
        ORDER BY vr.administeredDate DESC
        """)
    List<VaccinationRecord> findWithAdverseReactions();

    /**
     * Busca vacunas con reacciones adversas de un paciente
     */
    List<VaccinationRecord> findByPatientIdAndHadReactionTrue(Long patientId);

    /**
     * Busca vacunas válidas para viaje
     */
    @Query("""
        SELECT vr FROM VaccinationRecord vr
        WHERE vr.patient.id = :patientId
        AND vr.validForTravel = true
        AND vr.verified = true
        ORDER BY vr.administeredDate DESC
        """)
    List<VaccinationRecord> findTravelValidVaccines(@Param("patientId") Long patientId);

    /**
     * Busca dosis de refuerzo (boosters)
     */
    List<VaccinationRecord> findByPatientIdAndBoosterTrue(Long patientId);

    /**
     * Busca registros no verificados
     */
    @Query("""
        SELECT vr FROM VaccinationRecord vr
        WHERE vr.verified = false
        ORDER BY vr.administeredDate DESC
        """)
    List<VaccinationRecord> findUnverifiedRecords();

    /**
     * Busca por número de certificado
     */
    List<VaccinationRecord> findByCertificateNumber(String certificateNumber);

    /**
     * Busca vacunas administradas en un rango de fechas
     */
    @Query("""
        SELECT vr FROM VaccinationRecord vr
        WHERE vr.administeredDate BETWEEN :startDate AND :endDate
        ORDER BY vr.administeredDate DESC
        """)
    List<VaccinationRecord> findByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Cuenta registros de vacunación de un paciente
     */
    long countByPatientId(Long patientId);

    /**
     * Verifica si un paciente tiene esquema completo de una vacuna
     */
    @Query("""
        SELECT COUNT(vr) > 0 FROM VaccinationRecord vr
        WHERE vr.patient.id = :patientId
        AND LOWER(vr.vaccineName) LIKE LOWER(CONCAT('%', :vaccineName, '%'))
        AND vr.doseNumber = vr.totalDosesRequired
        """)
    boolean hasCompletedVaccineScheme(
            @Param("patientId") Long patientId,
            @Param("vaccineName") String vaccineName
    );

    /**
     * Vacunas más administradas
     */
    @Query("""
        SELECT vr.vaccineName, COUNT(vr)
        FROM VaccinationRecord vr
        GROUP BY vr.vaccineName
        ORDER BY COUNT(vr) DESC
        """)
    List<Object[]> findMostAdministeredVaccines(Pageable pageable);

    /**
     * Busca registros por fabricante
     */
    @Query("""
        SELECT vr FROM VaccinationRecord vr
        WHERE LOWER(vr.manufacturer) LIKE LOWER(CONCAT('%', :manufacturer, '%'))
        ORDER BY vr.administeredDate DESC
        """)
    List<VaccinationRecord> findByManufacturer(@Param("manufacturer") String manufacturer);
}
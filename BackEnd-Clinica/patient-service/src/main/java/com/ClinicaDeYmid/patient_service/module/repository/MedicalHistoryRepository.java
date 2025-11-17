package com.ClinicaDeYmid.patient_service.module.repository;

import com.ClinicaDeYmid.patient_service.module.entity.MedicalHistory;
import com.ClinicaDeYmid.patient_service.module.enums.BloodType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MedicalHistoryRepository extends JpaRepository<MedicalHistory, Long> {

    /**
     * Busca la historia médica por ID de paciente
     */
    Optional<MedicalHistory> findByPatientId(Long patientId);

    /**
     * Busca la historia médica por número de identificación del paciente
     */
    @Query("""
        SELECT mh FROM MedicalHistory mh
        JOIN mh.patient p
        WHERE p.identificationNumber = :identificationNumber
        """)
    Optional<MedicalHistory> findByPatientIdentificationNumber(@Param("identificationNumber") String identificationNumber);

    /**
     * Verifica si existe historia médica para un paciente
     */
    boolean existsByPatientId(Long patientId);

    /**
     * Busca historias médicas por tipo de sangre
     */
    List<MedicalHistory> findByBloodType(BloodType bloodType);

    /**
     * Busca pacientes con chequeo próximo
     */
    @Query("""
        SELECT mh FROM MedicalHistory mh
        WHERE mh.nextCheckupDate IS NOT NULL
        AND mh.nextCheckupDate BETWEEN :startDate AND :endDate
        ORDER BY mh.nextCheckupDate ASC
        """)
    List<MedicalHistory> findUpcomingCheckups(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Busca pacientes sin chequeo reciente
     */
    @Query("""
        SELECT mh FROM MedicalHistory mh
        WHERE mh.lastCheckupDate IS NULL
        OR mh.lastCheckupDate < :beforeDate
        ORDER BY mh.lastCheckupDate ASC NULLS FIRST
        """)
    List<MedicalHistory> findPatientsNeedingCheckup(@Param("beforeDate") LocalDate beforeDate);

    /**
     * Busca historias médicas con seguro
     */
    List<MedicalHistory> findByHasInsurance(Boolean hasInsurance);

    /**
     * Busca historias médicas por proveedor de seguro
     */
    @Query("""
        SELECT mh FROM MedicalHistory mh
        WHERE mh.hasInsurance = true
        AND LOWER(mh.insuranceProvider) LIKE LOWER(CONCAT('%', :provider, '%'))
        """)
    List<MedicalHistory> findByInsuranceProvider(@Param("provider") String provider);

    /**
     * Busca pacientes con IMC fuera del rango saludable
     */
    @Query("""
        SELECT mh FROM MedicalHistory mh
        WHERE mh.bmi IS NOT NULL
        AND (mh.bmi < :minBmi OR mh.bmi > :maxBmi)
        """)
    List<MedicalHistory> findByBmiOutOfRange(
            @Param("minBmi") Double minBmi,
            @Param("maxBmi") Double maxBmi
    );

    /**
     * Busca fumadores activos
     */
    @Query("""
        SELECT mh FROM MedicalHistory mh
        WHERE mh.smokingStatus IN ('CURRENT_SMOKER', 'HEAVY_SMOKER')
        """)
    List<MedicalHistory> findSmokers();

    /**
     * Cuenta historias médicas por tipo de sangre
     */
    @Query("""
        SELECT mh.bloodType, COUNT(mh)
        FROM MedicalHistory mh
        WHERE mh.bloodType IS NOT NULL
        GROUP BY mh.bloodType
        """)
    List<Object[]> countByBloodType();
}
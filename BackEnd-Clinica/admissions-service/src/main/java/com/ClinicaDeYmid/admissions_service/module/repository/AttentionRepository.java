package com.ClinicaDeYmid.admissions_service.module.repository;

import com.ClinicaDeYmid.admissions_service.module.entity.Attention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AttentionRepository extends JpaRepository<Attention, Long>, JpaSpecificationExecutor<Attention> {


    Optional<Attention> findByPatientIdAndDischargeeDateTimeIsNull(Long patientId);

    List<Attention> findByHealthProviderNitContainingOrderByAdmissionDateTimeDesc(String healthProviderNit);

    List<Attention> findByConfigurationServiceIdOrderByAdmissionDateTimeDesc(Long configServiceId);

    long countByDischargeeDateTimeIsNull();

    long countByPatientId(Long patientId);

    boolean existsByPatientIdAndDischargeeDateTimeIsNull(Long patientId);

    List<Attention> findByPatientIdOrderByAdmissionDateTimeDesc(Long patientId);

    List<Attention> findByDoctorIdOrderByAdmissionDateTimeDesc(Long doctorId);


    List<Attention> findByInvoicedTrue();

    List<Attention> findByInvoicedFalse();

    @Query("SELECT a FROM Attention a WHERE a.admissionDateTime BETWEEN :startDate AND :endDate")
    List<Attention> findByAdmissionDateTimeBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT a FROM Attention a WHERE a.dischargeDateTime BETWEEN :startDate AND :endDate")
    List<Attention> findByDischargeDateTimeBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );


    long countByInvoicedTrue();

    long countByInvoicedFalse();

    @Query("SELECT COUNT(a) FROM Attention a WHERE a.admissionDateTime BETWEEN :startDate AND :endDate")
    long countByAdmissionDateTimeBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );


    @Query("SELECT a FROM Attention a WHERE a.patientId = :patientId AND a.invoiced = false")
    List<Attention> findUnInvoicedAttentionsByPatientId(@Param("patientId") Long patientId);

    @Query("SELECT a FROM Attention a WHERE a.doctorId = :doctorId AND a.dischargeDateTime IS NULL")
    List<Attention> findActiveAttentionsByDoctorId(@Param("doctorId") Long doctorId);


    @Query("SELECT a FROM Attention a WHERE a.triageLevel = :triageLevel AND a.admissionDateTime BETWEEN :startDate AND :endDate")
    List<Attention> findByTriageLevelAndDateRange(
            @Param("triageLevel") String triageLevel,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

}
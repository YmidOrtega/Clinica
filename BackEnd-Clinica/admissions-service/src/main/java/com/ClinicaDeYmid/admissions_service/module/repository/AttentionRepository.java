package com.ClinicaDeYmid.admissions_service.module.repository;

import com.ClinicaDeYmid.admissions_service.module.entity.Attention;
import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AttentionRepository extends JpaRepository<Attention, Long>, JpaSpecificationExecutor<Attention> {

    // Solo un @EntityGraph por colección para evitar MultipleBagFetchException.
    // Las colecciones restantes (diagnosticCodes, userHistory) usan @BatchSize(25) en la entidad.
    @EntityGraph(attributePaths = {"authorizations"})
    List<Attention> findByPatientId(Long patientId);

    @EntityGraph(attributePaths = {"authorizations"})
    Optional<Attention> findByPatientIdAndStatus(Long patientId, AttentionStatus status);

    @EntityGraph(attributePaths = {"authorizations"})
    List<Attention> findByDoctorId(Long doctorId);

    @Query("SELECT DISTINCT a FROM Attention a JOIN a.healthProviderNit hp WHERE hp.healthProviderNit = :nit")
    List<Attention> findByHealthProviderNit(@Param("nit") String nit);

    @Query("SELECT a FROM Attention a WHERE a.configurationService.id = :configServiceId")
    List<Attention> findByConfigurationServiceId(@Param("configServiceId") Long configurationServiceId);

    @Query(value = "SELECT * FROM attentions WHERE id = :id", nativeQuery = true)
    Optional<Attention> findByIdIncludingDeleted(@Param("id") Long id);

}

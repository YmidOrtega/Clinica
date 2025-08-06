package com.ClinicaDeYmid.admissions_service.module.repository;

import com.ClinicaDeYmid.admissions_service.module.entity.Attention;
import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface AttentionRepository extends JpaRepository<Attention, Long>, JpaSpecificationExecutor<Attention> {
    
    List<Attention> findByPatientId(Long patientId);
    Optional<Attention> findByPatientIdAndStatus(Long patientId, AttentionStatus status);
    List<Attention> findByDoctorId(Long doctorId);
    List<Attention> findByHealthProviderNit(String nit);
    List<Attention> findByConfigurationServiceId(Long configurationServiceId);

}
package com.ClinicaDeYmid.admissions_service.module.repository;

import com.ClinicaDeYmid.admissions_service.module.entity.Attention;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttentionRepository extends JpaRepository <Attention, Long> {

}

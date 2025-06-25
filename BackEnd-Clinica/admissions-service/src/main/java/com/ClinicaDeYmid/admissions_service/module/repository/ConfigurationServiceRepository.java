package com.ClinicaDeYmid.admissions_service.module.repository;

import com.ClinicaDeYmid.admissions_service.module.entity.ConfigurationService;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;

@Repository
public interface ConfigurationServiceRepository extends JpaRepository<ConfigurationService, Long> {
}
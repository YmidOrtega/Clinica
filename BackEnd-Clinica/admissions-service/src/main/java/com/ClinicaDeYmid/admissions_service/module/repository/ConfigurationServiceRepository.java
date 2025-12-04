package com.ClinicaDeYmid.admissions_service.module.repository;

import com.ClinicaDeYmid.admissions_service.module.entity.ConfigurationService;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfigurationServiceRepository extends JpaRepository<ConfigurationService, Long> {
    
    @EntityGraph(attributePaths = {"serviceType", "serviceType.careTypes", "location"})
    @Query("SELECT cs FROM ConfigurationService cs WHERE cs.id = :id AND cs.deletedAt IS NULL")
    Optional<ConfigurationService> findByIdWithRelations(@Param("id") Long id);
}
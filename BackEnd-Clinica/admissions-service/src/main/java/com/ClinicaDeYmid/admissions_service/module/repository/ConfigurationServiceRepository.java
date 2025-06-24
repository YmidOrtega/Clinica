package com.ClinicaDeYmid.admissions_service.module.repository;

import com.ClinicaDeYmid.admissions_service.module.entity.ConfigurationService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConfigurationServiceRepository extends JpaRepository<ConfigurationService, Long> {

    List<ConfigurationService> findByActiveTrue();

    List<ConfigurationService> findByActiveFalse();

    boolean existsByIdAndActiveTrue(Long id);

    Optional<ConfigurationService> findByNameAndActiveTrue(String name);

    List<ConfigurationService> findByNameContainingIgnoreCaseAndActiveTrue(String name);

    @Query("SELECT cs FROM ConfigurationService cs WHERE cs.id = :id AND cs.active = true")
    Optional<ConfigurationService> findActiveById(@Param("id") Long id);

    long countByActiveTrue();

    @Query("SELECT CASE WHEN COUNT(cs) > 0 THEN true ELSE false END FROM ConfigurationService cs WHERE cs.id = :id AND cs.active = true")
    boolean isServiceAvailableForAttention(@Param("id") Long id);

    List<ConfigurationService> findByTypeAndActiveTrue(String type);

    List<ConfigurationService> findByDepartmentAndActiveTrue(String department);

    @Query("SELECT cs FROM ConfigurationService cs LEFT JOIN cs.attentions a GROUP BY cs ORDER BY COUNT(a) DESC")
    List<ConfigurationService> findOrderByAttentionCountDesc();
}
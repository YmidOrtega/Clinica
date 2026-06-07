package com.ClinicaDeYmid.billing_service.module.pricing.repository;

import com.ClinicaDeYmid.billing_service.module.pricing.entity.PriceManual;
import com.ClinicaDeYmid.billing_service.module.pricing.enums.PriceManualType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PriceManualRepository extends JpaRepository<PriceManual, Long> {

    Optional<PriceManual> findByCode(String code);

    boolean existsByCode(String code);

    List<PriceManual> findByActiveTrueOrderByTypeAscNameAsc();

    List<PriceManual> findByTypeAndActiveTrueOrderByYearDesc(PriceManualType type);

    @Query("""
            SELECT m FROM PriceManual m
            WHERE m.type = :type
            AND m.year = :year
            AND m.active = true
            """)
    Optional<PriceManual> findByTypeAndYear(
            @Param("type") PriceManualType type,
            @Param("year") Short year);

    @Query("""
            SELECT m FROM PriceManual m
            WHERE LOWER(m.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
            AND m.active = true
            ORDER BY m.name ASC
            """)
    Page<PriceManual> searchByName(@Param("searchTerm") String searchTerm, Pageable pageable);
}

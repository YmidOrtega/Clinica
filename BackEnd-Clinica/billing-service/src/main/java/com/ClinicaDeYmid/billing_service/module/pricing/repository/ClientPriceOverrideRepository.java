package com.ClinicaDeYmid.billing_service.module.pricing.repository;

import com.ClinicaDeYmid.billing_service.module.pricing.entity.ClientPriceOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientPriceOverrideRepository extends JpaRepository<ClientPriceOverride, Long> {

    List<ClientPriceOverride> findByContractIdAndActiveTrueOrderByPortfolioIdAsc(Long contractId);

    List<ClientPriceOverride> findByHealthProviderNitAndActiveTrue(String healthProviderNit);

    boolean existsByContractIdAndPortfolioIdAndActiveTrue(Long contractId, Long portfolioId);

    @Query("""
            SELECT o FROM ClientPriceOverride o
            WHERE o.contractId = :contractId
            AND o.portfolioId = :portfolioId
            AND o.active = true
            AND o.validFrom <= :today
            AND o.validTo >= :today
            """)
    Optional<ClientPriceOverride> findValidOverride(
            @Param("contractId") Long contractId,
            @Param("portfolioId") Long portfolioId,
            @Param("today") LocalDate today);

    @Query("""
            SELECT o FROM ClientPriceOverride o
            WHERE o.contractId = :contractId
            AND o.portfolioId = :portfolioId
            AND o.active = true
            """)
    Optional<ClientPriceOverride> findActiveByContractAndPortfolio(
            @Param("contractId") Long contractId,
            @Param("portfolioId") Long portfolioId);

    @Query("""
            SELECT o FROM ClientPriceOverride o
            WHERE o.active = true
            AND o.validTo < :today
            ORDER BY o.validTo ASC
            """)
    List<ClientPriceOverride> findExpired(@Param("today") LocalDate today);
}

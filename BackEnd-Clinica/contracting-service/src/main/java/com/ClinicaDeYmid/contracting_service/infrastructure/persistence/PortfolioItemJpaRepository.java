package com.ClinicaDeYmid.contracting_service.infrastructure.persistence;

import com.ClinicaDeYmid.contracting_service.domain.PortfolioItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PortfolioItemJpaRepository extends JpaRepository<PortfolioItem, Long> {

    Optional<PortfolioItem> findByUuid(UUID uuid);

    @Query("SELECT i FROM PortfolioItem i WHERE i.code.clinic = :code")
    Optional<PortfolioItem> findByClinicCode(@Param("code") String code);

    @Query("SELECT i FROM PortfolioItem i WHERE i.code.cups = :code ORDER BY i.code.clinic")
    List<PortfolioItem> findByCupsCode(@Param("code") String code);

    @Query("SELECT i FROM PortfolioItem i WHERE i.name LIKE :prefix ORDER BY i.name")
    Page<PortfolioItem> searchByName(@Param("prefix") String prefix, Pageable pageable);
}

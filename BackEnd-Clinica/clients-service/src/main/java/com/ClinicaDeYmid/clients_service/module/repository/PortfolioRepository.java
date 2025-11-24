package com.ClinicaDeYmid.clients_service.module.repository;

import com.ClinicaDeYmid.clients_service.module.entity.Portfolio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    /**
     * Busca un portfolio por ID incluyendo eliminados lógicamente
     */
    @Query("SELECT p FROM Portfolio p WHERE p.id = :id")
    Optional<Portfolio> findByIdIncludingDeleted(@Param("id") Long id);

    /**
     * Obtiene todos los portfolios activos con paginación
     */
    @Query("SELECT p FROM Portfolio p " +
            "WHERE p.deletedAt IS NULL " +
            "ORDER BY p.createdAt DESC")
    Page<Portfolio> findAllActive(Pageable pageable);

    /**
     * Busca portfolios eliminados (para auditoría)
     */
    @Query("SELECT p FROM Portfolio p " +
            "WHERE p.deletedAt IS NOT NULL " +
            "ORDER BY p.deletedAt DESC")
    Page<Portfolio> findDeleted(Pageable pageable);

    /**
     * Busca portfolios creados por un usuario específico
     */
    @Query("SELECT p FROM Portfolio p " +
            "WHERE p.createdBy = :userId " +
            "AND p.deletedAt IS NULL " +
            "ORDER BY p.createdAt DESC")
    Page<Portfolio> findByCreatedBy(
            @Param("userId") Long userId,
            Pageable pageable);
}
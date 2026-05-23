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

    @Query("SELECT p FROM Portfolio p WHERE p.id = :id")
    Optional<Portfolio> findByIdIncludingDeleted(@Param("id") Long id);

    @Query("""
        SELECT p FROM Portfolio p
        WHERE p.deletedAt IS NULL
        ORDER BY p.createdAt DESC
        """)
    Page<Portfolio> findAllActive(Pageable pageable);

    @Query("""
        SELECT p FROM Portfolio p
        WHERE p.deletedAt IS NOT NULL
        ORDER BY p.deletedAt DESC
        """)
    Page<Portfolio> findDeleted(Pageable pageable);

    @Query("""
        SELECT p FROM Portfolio p
        WHERE p.createdBy = :userId
        AND p.deletedAt IS NULL
        ORDER BY p.createdAt DESC
        """)
    Page<Portfolio> findByCreatedBy(
            @Param("userId") Long userId,
            Pageable pageable);
}

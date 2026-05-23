package com.ClinicaDeYmid.clients_service.module.repository;

import com.ClinicaDeYmid.clients_service.module.entity.Contract;
import com.ClinicaDeYmid.clients_service.module.enums.ContractStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {

    Optional<Contract> findByContractNumber(String contractNumber);

    boolean existsByContractNumber(String contractNumber);

    @Query("SELECT c FROM Contract c WHERE c.id = :id")
    Optional<Contract> findByIdIncludingDeleted(@Param("id") Long id);

    @Query("SELECT c FROM Contract c LEFT JOIN FETCH c.healthProvider WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<Contract> findByIdWithProvider(@Param("id") Long id);

    @Query("""
        SELECT c FROM Contract c
        WHERE c.healthProvider.id = :healthProviderId
        AND c.deletedAt IS NULL
        ORDER BY c.startDate DESC
        """)
    List<Contract> findByHealthProviderId(@Param("healthProviderId") Long healthProviderId);

    @Query("""
        SELECT c FROM Contract c
        WHERE c.healthProvider.id = :healthProviderId
        AND c.active = true
        AND c.deletedAt IS NULL
        ORDER BY c.startDate DESC
        """)
    List<Contract> findActiveByHealthProviderId(@Param("healthProviderId") Long healthProviderId);

    @Query("""
        SELECT c FROM Contract c
        WHERE c.active = true
        AND c.deletedAt IS NULL
        ORDER BY c.startDate DESC
        """)
    Page<Contract> findAllActive(Pageable pageable);

    @Query("""
        SELECT c FROM Contract c
        WHERE c.status = :status
        AND c.deletedAt IS NULL
        ORDER BY c.startDate DESC
        """)
    Page<Contract> findByStatus(
            @Param("status") ContractStatus status,
            Pageable pageable);

    @Query("""
        SELECT c FROM Contract c
        WHERE c.active = true
        AND c.deletedAt IS NULL
        AND c.startDate <= :currentDate
        AND c.endDate >= :currentDate
        ORDER BY c.endDate ASC
        """)
    Page<Contract> findCurrentlyValid(
            @Param("currentDate") LocalDate currentDate,
            Pageable pageable);

    @Query("""
        SELECT c FROM Contract c
        WHERE c.active = true
        AND c.deletedAt IS NULL
        AND c.endDate BETWEEN :startDate AND :endDate
        ORDER BY c.endDate ASC
        """)
    List<Contract> findExpiringBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
        SELECT c FROM Contract c
        WHERE c.active = true
        AND c.deletedAt IS NULL
        AND c.endDate < :currentDate
        ORDER BY c.endDate DESC
        """)
    Page<Contract> findExpired(
            @Param("currentDate") LocalDate currentDate,
            Pageable pageable);

    @Query("""
        SELECT COUNT(c) FROM Contract c
        WHERE c.healthProvider.id = :healthProviderId
        AND c.active = true
        AND c.deletedAt IS NULL
        """)
    long countActiveByHealthProviderId(@Param("healthProviderId") Long healthProviderId);

    @Query("""
        SELECT c FROM Contract c
        WHERE c.deletedAt IS NULL
        AND ((c.startDate BETWEEN :startDate AND :endDate)
        OR (c.endDate BETWEEN :startDate AND :endDate)
        OR (c.startDate <= :startDate AND c.endDate >= :endDate))
        ORDER BY c.startDate DESC
        """)
    Page<Contract> findByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    @Query("""
        SELECT c FROM Contract c
        WHERE c.agreedTariff BETWEEN :minTariff AND :maxTariff
        AND c.deletedAt IS NULL
        ORDER BY c.agreedTariff DESC
        """)
    Page<Contract> findByTariffRange(
            @Param("minTariff") BigDecimal minTariff,
            @Param("maxTariff") BigDecimal maxTariff,
            Pageable pageable);

    @Query("""
        SELECT c FROM Contract c
        WHERE LOWER(c.contractName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
        AND c.deletedAt IS NULL
        ORDER BY c.contractName ASC
        """)
    Page<Contract> searchByName(
            @Param("searchTerm") String searchTerm,
            Pageable pageable);

    @Query("""
        SELECT c FROM Contract c
        WHERE c.deletedAt IS NOT NULL
        ORDER BY c.deletedAt DESC
        """)
    Page<Contract> findDeleted(Pageable pageable);

    @Query("""
        SELECT c FROM Contract c
        WHERE c.createdBy = :userId
        AND c.deletedAt IS NULL
        ORDER BY c.createdAt DESC
        """)
    Page<Contract> findByCreatedBy(
            @Param("userId") Long userId,
            Pageable pageable);

    @Query("""
        SELECT c FROM Contract c
        WHERE c.updatedAt >= :since
        AND c.deletedAt IS NULL
        ORDER BY c.updatedAt DESC
        """)
    Page<Contract> findRecentlyUpdated(
            @Param("since") LocalDateTime since,
            Pageable pageable);

    @Query("""
        SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
        FROM Contract c
        WHERE c.healthProvider.id = :healthProviderId
        AND c.deletedAt IS NULL
        AND ((c.startDate BETWEEN :startDate AND :endDate)
        OR (c.endDate BETWEEN :startDate AND :endDate)
        OR (c.startDate <= :startDate AND c.endDate >= :endDate))
        """)
    boolean hasContractsInDateRange(
            @Param("healthProviderId") Long healthProviderId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
        SELECT c FROM Contract c
        WHERE c.healthProvider.id = :healthProviderId
        AND c.deletedAt IS NULL
        ORDER BY c.startDate DESC
        """)
    Optional<Contract> findMostRecentByHealthProviderId(@Param("healthProviderId") Long healthProviderId);

    @Query("""
        SELECT c FROM Contract c
        WHERE c.healthProvider.id = :healthProviderId
        AND c.status = :status
        AND c.deletedAt IS NULL
        ORDER BY c.startDate DESC
        """)
    List<Contract> findByHealthProviderIdAndStatus(
            @Param("healthProviderId") Long healthProviderId,
            @Param("status") ContractStatus status);

    @Query("""
        SELECT c FROM Contract c
        WHERE c.healthProvider.id = :healthProviderId
        AND c.deletedAt IS NOT NULL
        ORDER BY c.deletedAt DESC
        """)
    Page<Contract> findDeletedByHealthProviderId(
            @Param("healthProviderId") Long healthProviderId,
            Pageable pageable);
}

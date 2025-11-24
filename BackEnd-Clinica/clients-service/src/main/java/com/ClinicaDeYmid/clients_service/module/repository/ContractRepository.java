package com.ClinicaDeYmid.clients_service.module.repository;

import com.ClinicaDeYmid.clients_service.module.entity.Contract;
import com.ClinicaDeYmid.clients_service.module.enums.ContractStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {

    /**
     * Busca un contrato por número de contrato
     */
    Optional<Contract> findByContractNumber(String contractNumber);

    /**
     * Verifica si existe un contrato con el número especificado
     */
    boolean existsByContractNumber(String contractNumber);

    /**
     * Busca un contrato por ID incluyendo eliminados lógicamente
     */
    @Query("SELECT c FROM Contract c WHERE c.id = :id")
    Optional<Contract> findByIdIncludingDeleted(@Param("id") Long id);

    /**
     * Obtiene todos los contratos de un proveedor específico
     */
    @Query("SELECT c FROM Contract c " +
            "WHERE c.healthProvider.id = :healthProviderId " +
            "AND c.deletedAt IS NULL " +
            "ORDER BY c.startDate DESC")
    List<Contract> findByHealthProviderId(@Param("healthProviderId") Long healthProviderId);

    /**
     * Obtiene contratos activos de un proveedor específico
     */
    @Query("SELECT c FROM Contract c " +
            "WHERE c.healthProvider.id = :healthProviderId " +
            "AND c.active = true " +
            "AND c.deletedAt IS NULL " +
            "ORDER BY c.startDate DESC")
    List<Contract> findActiveByHealthProviderId(@Param("healthProviderId") Long healthProviderId);

    /**
     * Obtiene todos los contratos activos con paginación
     */
    @Query("SELECT c FROM Contract c " +
            "WHERE c.active = true " +
            "AND c.deletedAt IS NULL " +
            "ORDER BY c.startDate DESC")
    Page<Contract> findAllActive(Pageable pageable);

    /**
     * Busca contratos por estado
     */
    @Query("SELECT c FROM Contract c " +
            "WHERE c.status = :status " +
            "AND c.deletedAt IS NULL " +
            "ORDER BY c.startDate DESC")
    Page<Contract> findByStatus(
            @Param("status") ContractStatus status,
            Pageable pageable);

    /**
     * Busca contratos vigentes (activos y dentro del rango de fechas)
     */
    @Query("SELECT c FROM Contract c " +
            "WHERE c.active = true " +
            "AND c.deletedAt IS NULL " +
            "AND c.startDate <= :currentDate " +
            "AND c.endDate >= :currentDate " +
            "ORDER BY c.endDate ASC")
    Page<Contract> findCurrentlyValid(
            @Param("currentDate") LocalDate currentDate,
            Pageable pageable);

    /**
     * Busca contratos próximos a vencer
     */
    @Query("SELECT c FROM Contract c " +
            "WHERE c.active = true " +
            "AND c.deletedAt IS NULL " +
            "AND c.endDate BETWEEN :startDate AND :endDate " +
            "ORDER BY c.endDate ASC")
    List<Contract> findExpiringBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Busca contratos vencidos
     */
    @Query("SELECT c FROM Contract c " +
            "WHERE c.active = true " +
            "AND c.deletedAt IS NULL " +
            "AND c.endDate < :currentDate " +
            "ORDER BY c.endDate DESC")
    Page<Contract> findExpired(
            @Param("currentDate") LocalDate currentDate,
            Pageable pageable);

    /**
     * Cuenta contratos activos de un proveedor
     */
    @Query("SELECT COUNT(c) FROM Contract c " +
            "WHERE c.healthProvider.id = :healthProviderId " +
            "AND c.active = true " +
            "AND c.deletedAt IS NULL")
    long countActiveByHealthProviderId(@Param("healthProviderId") Long healthProviderId);

    /**
     * Obtiene contratos por rango de fechas
     */
    @Query("SELECT c FROM Contract c " +
            "WHERE c.deletedAt IS NULL " +
            "AND ((c.startDate BETWEEN :startDate AND :endDate) " +
            "OR (c.endDate BETWEEN :startDate AND :endDate) " +
            "OR (c.startDate <= :startDate AND c.endDate >= :endDate)) " +
            "ORDER BY c.startDate DESC")
    Page<Contract> findByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    /**
     * Busca contratos con tarifa en un rango específico
     */
    @Query("SELECT c FROM Contract c " +
            "WHERE c.agreedTariff BETWEEN :minTariff AND :maxTariff " +
            "AND c.deletedAt IS NULL " +
            "ORDER BY c.agreedTariff DESC")
    Page<Contract> findByTariffRange(
            @Param("minTariff") java.math.BigDecimal minTariff,
            @Param("maxTariff") java.math.BigDecimal maxTariff,
            Pageable pageable);

    /**
     * Busca contratos por nombre (búsqueda parcial)
     */
    @Query("SELECT c FROM Contract c " +
            "WHERE LOWER(c.contractName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "AND c.deletedAt IS NULL " +
            "ORDER BY c.contractName ASC")
    Page<Contract> searchByName(
            @Param("searchTerm") String searchTerm,
            Pageable pageable);

    /**
     * Obtiene contratos eliminados (para auditoría)
     */
    @Query("SELECT c FROM Contract c " +
            "WHERE c.deletedAt IS NOT NULL " +
            "ORDER BY c.deletedAt DESC")
    Page<Contract> findDeleted(Pageable pageable);

    /**
     * Busca contratos creados por un usuario específico
     */
    @Query("SELECT c FROM Contract c " +
            "WHERE c.createdBy = :userId " +
            "AND c.deletedAt IS NULL " +
            "ORDER BY c.createdAt DESC")
    Page<Contract> findByCreatedBy(
            @Param("userId") Long userId,
            Pageable pageable);

    /**
     * Busca contratos actualizados recientemente
     */
    @Query("SELECT c FROM Contract c " +
            "WHERE c.updatedAt >= :since " +
            "AND c.deletedAt IS NULL " +
            "ORDER BY c.updatedAt DESC")
    Page<Contract> findRecentlyUpdated(
            @Param("since") java.time.LocalDateTime since,
            Pageable pageable);

    /**
     * Verifica si un proveedor tiene contratos en un rango de fechas
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
            "FROM Contract c " +
            "WHERE c.healthProvider.id = :healthProviderId " +
            "AND c.deletedAt IS NULL " +
            "AND ((c.startDate BETWEEN :startDate AND :endDate) " +
            "OR (c.endDate BETWEEN :startDate AND :endDate) " +
            "OR (c.startDate <= :startDate AND c.endDate >= :endDate))")
    boolean hasContractsInDateRange(
            @Param("healthProviderId") Long healthProviderId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Obtiene el contrato más reciente de un proveedor
     */
    @Query("SELECT c FROM Contract c " +
            "WHERE c.healthProvider.id = :healthProviderId " +
            "AND c.deletedAt IS NULL " +
            "ORDER BY c.startDate DESC")
    Optional<Contract> findMostRecentByHealthProviderId(@Param("healthProviderId") Long healthProviderId);

    /**
     * Busca contratos por estado y proveedor
     */
    @Query("SELECT c FROM Contract c " +
            "WHERE c.healthProvider.id = :healthProviderId " +
            "AND c.status = :status " +
            "AND c.deletedAt IS NULL " +
            "ORDER BY c.startDate DESC")
    List<Contract> findByHealthProviderIdAndStatus(
            @Param("healthProviderId") Long healthProviderId,
            @Param("status") ContractStatus status);

    /**
     * Busca contratos eliminados de un proveedor específico
     */
    @Query("SELECT c FROM Contract c " +
            "WHERE c.healthProvider.id = :healthProviderId " +
            "AND c.deletedAt IS NOT NULL " +
            "ORDER BY c.deletedAt DESC")
    Page<Contract> findDeletedByHealthProviderId(
            @Param("healthProviderId") Long healthProviderId,
            Pageable pageable);
}
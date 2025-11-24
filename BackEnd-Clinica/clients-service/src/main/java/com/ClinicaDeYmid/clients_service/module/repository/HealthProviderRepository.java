package com.ClinicaDeYmid.clients_service.module.repository;

import com.ClinicaDeYmid.clients_service.module.entity.HealthProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HealthProviderRepository extends JpaRepository<HealthProvider, Long> {

    /**
     * Busca un proveedor por el valor del NIT (excluyendo eliminados)
     */
    Optional<HealthProvider> findByNit_Value(String nitValue);

    /**
     * Verifica si existe un proveedor con el NIT especificado (excluyendo eliminados)
     */
    boolean existsByNit_Value(String nitValue);

    /**
     * Busca un proveedor por NIT incluyendo eliminados lógicamente
     */
    @Query(value = "SELECT * FROM health_providers WHERE nit = :nitValue", nativeQuery = true)
    Optional<HealthProvider> findByNitIncludingDeleted(@Param("nitValue") String nitValue);

    /**
     * Busca un proveedor por ID incluyendo eliminados lógicamente
     */
    @Query("SELECT hp FROM HealthProvider hp WHERE hp.id = :id")
    Optional<HealthProvider> findByIdIncludingDeleted(@Param("id") Long id);

    /**
     * Obtiene todos los proveedores activos con paginación
     * Usa @EntityGraph para cargar contratos de forma eficiente y evitar N+1
     */
    @Query("SELECT DISTINCT hp FROM HealthProvider hp " +
            "LEFT JOIN FETCH hp.contracts " +
            "WHERE hp.active = true AND hp.deletedAt IS NULL")
    Page<HealthProvider> findAllActive(Pageable pageable);

    /**
     * Busca proveedores activos por tipo de proveedor
     */
    @Query("SELECT hp FROM HealthProvider hp " +
            "WHERE hp.typeProvider = :typeProvider " +
            "AND hp.active = true " +
            "AND hp.deletedAt IS NULL")
    Page<HealthProvider> findActiveByTypeProvider(
            @Param("typeProvider") String typeProvider,
            Pageable pageable);

    /**
     * Busca un proveedor por NIT con sus contratos (optimizado con JOIN FETCH)
     */
    @Query(value = "SELECT hp.* FROM health_providers hp " +
            "LEFT JOIN contracts c ON hp.id = c.health_provider_id " +
            "WHERE hp.nit = :nitValue " +
            "AND hp.deleted_at IS NULL", nativeQuery = true)
    Optional<HealthProvider> findByNitWithContracts(@Param("nitValue") String nitValue);

    /**
     * Busca un proveedor por ID con sus contratos activos
     */
    @Query("SELECT hp FROM HealthProvider hp " +
            "LEFT JOIN FETCH hp.contracts c " +
            "WHERE hp.id = :id " +
            "AND hp.deletedAt IS NULL " +
            "AND (c.active = true OR c IS NULL)")
    Optional<HealthProvider> findByIdWithActiveContracts(@Param("id") Long id);

    /**
     * Cuenta proveedores activos
     */
    @Query("SELECT COUNT(hp) FROM HealthProvider hp " +
            "WHERE hp.active = true AND hp.deletedAt IS NULL")
    long countActiveProviders();

    /**
     * Busca proveedores por razón social (búsqueda parcial)
     */
    @Query("SELECT hp FROM HealthProvider hp " +
            "WHERE LOWER(hp.socialReason) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "AND hp.active = true " +
            "AND hp.deletedAt IS NULL")
    Page<HealthProvider> searchBySocialReason(
            @Param("searchTerm") String searchTerm,
            Pageable pageable);

    /**
     * Busca proveedores eliminados (para auditoría)
     */
    @Query("SELECT hp FROM HealthProvider hp " +
            "WHERE hp.deletedAt IS NOT NULL " +
            "ORDER BY hp.deletedAt DESC")
    Page<HealthProvider> findDeleted(Pageable pageable);

    /**
     * Obtiene proveedores con contratos activos
     */
    @Query("SELECT DISTINCT hp FROM HealthProvider hp " +
            "JOIN hp.contracts c " +
            "WHERE c.active = true " +
            "AND hp.active = true " +
            "AND hp.deletedAt IS NULL " +
            "AND c.deletedAt IS NULL")
    Page<HealthProvider> findProvidersWithActiveContracts(Pageable pageable);

    /**
     * Verifica si un proveedor tiene contratos activos
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
            "FROM HealthProvider hp " +
            "JOIN hp.contracts c " +
            "WHERE hp.id = :providerId " +
            "AND c.active = true " +
            "AND c.deletedAt IS NULL")
    boolean hasActiveContracts(@Param("providerId") Long providerId);

    /**
     * Busca proveedores creados por un usuario específico
     */
    @Query("SELECT hp FROM HealthProvider hp " +
            "WHERE hp.createdBy = :userId " +
            "AND hp.deletedAt IS NULL " +
            "ORDER BY hp.createdAt DESC")
    Page<HealthProvider> findByCreatedBy(
            @Param("userId") Long userId,
            Pageable pageable);

    /**
     * Busca proveedores actualizados recientemente
     */
    @Query("SELECT hp FROM HealthProvider hp " +
            "WHERE hp.updatedAt >= :since " +
            "AND hp.deletedAt IS NULL " +
            "ORDER BY hp.updatedAt DESC")
    Page<HealthProvider> findRecentlyUpdated(
            @Param("since") java.time.LocalDateTime since,
            Pageable pageable);
}